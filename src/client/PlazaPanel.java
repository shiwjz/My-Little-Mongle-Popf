package client;

import common.Gene;
import common.PartCatalog;
import common.Plaza;
import common.Pop;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 광장 화면 (사이드뷰, 좌우 이동).
 *
 * - 배경: bg/plaza.png (400x192 도트 권장) 가 있으면 그걸, 없으면 코드로 그린 임시 광장
 * - 방향키 ← → (또는 A, D)로 내 포프 이동. 채팅 입력칸에 글을 쓰는 중에는 움직이지 않는다.
 *   (광장을 한 번 클릭하면 채팅칸에서 포커스가 빠져서 바로 움직일 수 있음)
 * - 내 위치가 바뀌면 moveSender 로 서버에 /MOVE 를 보낸다 (0.1초에 한 번까지)
 * - 다른 사람 포프는 서버에서 받은 위치로 부드럽게 따라간다
 * - showBubble(닉네임, 내용): 포프 머리 위에 말풍선 (정헌이 광장 채팅을 붙일 때 사용)
 */
public class PlazaPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final long SEND_INTERVAL_MS = 100;
    private static final long BUBBLE_MS = 4000;
    private static final long NPC_TALK_MS = 7000;

    private static final Color INK = new Color(0x3A2E33);

    /** 광장에 보이는 포프 한 마리 */
    private static final class Avatar {
        final String name;
        final boolean npc;
        double x;           // 화면에 그리는 위치 (도트)
        int targetX;        // 서버에서 받은 위치
        String dir = "R";
        BufferedImage sprite;
        String bubble;
        long bubbleUntil;
        long lastMoveAt;

        Avatar(String name, boolean npc, int x) {
            this.name = name;
            this.npc = npc;
            this.x = x;
            this.targetX = x;
        }
    }

    private final String myName;
    private final Map<String, Avatar> avatars = new LinkedHashMap<>();
    private transient BiConsumer<Integer, String> moveSender = (x, d) -> { };
    private final BufferedImage background = loadBackground();

    private boolean active = false;
    private boolean leftDown = false;
    private boolean rightDown = false;
    private int lastSentX = -1;
    private long lastSentAt = 0;
    private long npcTalkAt = 0;
    private int npcTalkIndex = 0;
    private double t = 0;

    public PlazaPanel(String myName) {
        this.myName = myName;
        setPreferredSize(new Dimension(600, 576));
        setFocusable(true);

        // 광장을 클릭하면 채팅칸에서 포커스를 가져와서 방향키가 바로 먹게
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // 키 입력: 창 전체에서 받되, 글자 입력칸에 포커스가 있으면 무시
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this::onKey);

        new Timer(33, e -> tick()).start();
    }

    private static BufferedImage loadBackground() {
        File f = PartCatalog.file("bg", "plaza");
        if (!f.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 내 위치가 바뀌었을 때 서버로 보내는 함수 (x, "L"/"R") */
    public void setMoveSender(BiConsumer<Integer, String> sender) {
        this.moveSender = sender;
    }

    // ------------------------------------------------------------------
    // 서버 메시지에 따라 호출 (모두 EDT 에서)
    // ------------------------------------------------------------------

    /** /PLAZA_OK 를 받았을 때. 광장을 비우고 나와 NPC 를 놓는다 */
    public void enter(int myX, Pop myPop) {
        avatars.clear();
        for (Plaza.Npc npc : Plaza.NPCS) {
            Avatar a = new Avatar(npc.name, true, npc.x);
            Pop p = new Pop(Gene.parse(npc.gene));
            p.stage = Pop.Stage.ADULT;
            a.sprite = PopRenderer.render(p, PopRenderer.SIZE);
            avatars.put(npc.name, a);
        }
        Avatar me = new Avatar(myName, false, myX);
        if (myPop != null) {
            me.sprite = PopRenderer.render(myPop, PopRenderer.SIZE);
        }
        avatars.put(myName, me);
        lastSentX = myX;
        npcTalkAt = System.currentTimeMillis() + 1500;
        active = true;
        repaint();
    }

    /** 집으로 돌아갈 때 */
    public void leave() {
        active = false;
        leftDown = rightDown = false;
        avatars.clear();
    }

    /** /PLAZA_IN: 다른 사람이 들어왔거나 포프가 바뀜 */
    public void putOther(String name, int x, String dir, Pop pop) {
        if (name.equals(myName)) {
            return;
        }
        Avatar a = avatars.get(name);
        if (a == null) {
            a = new Avatar(name, false, x);
            avatars.put(name, a);
        }
        a.targetX = x;
        a.dir = dir;
        if (pop != null) {
            a.sprite = PopRenderer.render(pop, PopRenderer.SIZE);
        }
        repaint();
    }

    /** /PLAZA_OUT */
    public void removeOther(String name) {
        avatars.remove(name);
        repaint();
    }

    /** /MOVE 닉네임 x 방향 */
    public void moveOther(String name, int x, String dir) {
        Avatar a = avatars.get(name);
        if (a != null && !name.equals(myName)) {
            a.targetX = x;
            a.dir = dir;
            a.lastMoveAt = System.currentTimeMillis();
        }
    }

    /** 내 포프가 바뀌었을 때 (/POP) */
    public void updateMyPop(Pop pop) {
        Avatar me = avatars.get(myName);
        if (me != null) {
            me.sprite = PopRenderer.render(pop, PopRenderer.SIZE);
        }
    }

    /** 포프 머리 위에 말풍선을 띄운다. 광장에 없는 이름이면 무시 */
    public void showBubble(String name, String text) {
        Avatar a = avatars.get(name);
        if (a != null) {
            a.bubble = text;
            a.bubbleUntil = System.currentTimeMillis() + BUBBLE_MS;
            repaint();
        }
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------
    // 키 입력 / 움직임
    // ------------------------------------------------------------------

    private boolean onKey(KeyEvent e) {
        if (!active || !isShowing()) {
            return false;
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus instanceof JTextComponent) {
            return false; // 채팅 입력 중에는 방향키·A·D 가 글자 입력으로 가야 함
        }
        if (focus != null && SwingUtilities.getWindowAncestor(focus) != SwingUtilities.getWindowAncestor(this)) {
            return false;
        }
        boolean pressed = e.getID() == KeyEvent.KEY_PRESSED;
        boolean released = e.getID() == KeyEvent.KEY_RELEASED;
        if (!pressed && !released) {
            return false;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                leftDown = pressed;
                return true;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                rightDown = pressed;
                return true;
            default:
                return false;
        }
    }

    private void tick() {
        t += 0.08;
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();

        // 내 포프
        Avatar me = avatars.get(myName);
        if (me != null) {
            int dx = (rightDown ? 1 : 0) - (leftDown ? 1 : 0);
            if (dx != 0) {
                me.x = Plaza.clampX((int) Math.round(me.x) + dx * Plaza.SPEED);
                me.dir = dx < 0 ? "L" : "R";
                me.lastMoveAt = now;
            }
            int x = (int) Math.round(me.x);
            if (x != lastSentX && now - lastSentAt >= SEND_INTERVAL_MS) {
                moveSender.accept(x, me.dir);
                lastSentX = x;
                lastSentAt = now;
            }
        }

        // 다른 사람: 받은 위치로 부드럽게
        for (Avatar a : avatars.values()) {
            if (a != me && !a.npc) {
                a.x += (a.targetX - a.x) * 0.3;
                if (Math.abs(a.targetX - a.x) < 0.5) {
                    a.x = a.targetX;
                }
            }
        }

        // NPC 대사 돌리기
        if (now >= npcTalkAt && !Plaza.NPCS.isEmpty()) {
            Plaza.Npc npc = Plaza.NPCS.get(npcTalkIndex % Plaza.NPCS.size());
            String line = npc.lines[(npcTalkIndex / Plaza.NPCS.size()) % npc.lines.length];
            showBubble(npc.name, line);
            npcTalkIndex++;
            npcTalkAt = now + NPC_TALK_MS;
        }

        repaint();
    }

    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth();
        int h = getHeight();
        int scale = Math.max(1, Math.min(3, h / Plaza.MAP_H));
        int viewW = Math.max(1, w / scale);           // 화면에 보이는 가로 도트 수
        int offsetY = (h - Plaza.MAP_H * scale) / 2;   // 세로 가운데 정렬

        // 카메라: 내 포프를 가운데에 (맵 끝에서는 멈춤)
        Avatar me = avatars.get(myName);
        double camX = 0;
        if (Plaza.MAP_W > viewW) {
            double center = me != null ? me.x : Plaza.START_X;
            camX = Math.max(0, Math.min(Plaza.MAP_W - viewW, center - viewW / 2.0));
        } else {
            camX = -(viewW - Plaza.MAP_W) / 2.0; // 맵이 화면보다 좁으면 가운데
        }

        g.setColor(new Color(0xDFF3FF));
        g.fillRect(0, 0, w, h);
        Graphics2D world = (Graphics2D) g.create();
        world.translate(-camX * scale, offsetY);
        drawBackground(world, scale);

        // 포프들 (뒤에 있는 것부터: 그냥 x 순서)
        List<Avatar> list = new ArrayList<>(avatars.values());
        list.sort((a, b) -> Double.compare(a.x, b.x));
        long now = System.currentTimeMillis();
        for (Avatar a : list) {
            drawAvatar(world, a, scale, now);
        }
        for (Avatar a : list) {
            if (a.bubble != null && now < a.bubbleUntil) {
                drawBubble(world, a, scale);
            }
        }
        world.dispose();

        // 안내 문구
        g.setColor(INK);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString("포프 광장", 16, 26);
        g.setFont(getFont().deriveFont(12f));
        g.setColor(new Color(0x6B7F8A));
        g.drawString(isFocusOwner() ? "← → 또는 A D 로 이동" : "광장을 클릭하면 ← → 로 움직일 수 있어요", 16, 46);
        g.drawString("접속 " + (int) avatars.values().stream().filter(a -> !a.npc).count() + "명", w - 70, 26);
        g.dispose();
    }

    private void drawAvatar(Graphics2D g, Avatar a, int scale, long now) {
        if (a.sprite == null) {
            return;
        }
        int size = 64 * scale;
        boolean walking = now - a.lastMoveAt < 150 || Math.abs(a.targetX - a.x) > 0.5;
        int hop = walking ? (int) Math.round(Math.abs(Math.sin(t * 3)) * 2 * scale) : (int) Math.round(Math.sin(t + a.name.hashCode()) * scale);
        int feetX = (int) Math.round(a.x * scale);
        int feetY = Plaza.FEET_Y * scale;
        int sx = feetX - size / 2;
        int sy = feetY - 60 * scale - hop;

        g.setColor(new Color(0, 0, 0, 30));
        g.fillOval(feetX - size / 4, feetY - 4, size / 2, 10);
        g.drawImage(a.sprite, sx, sy, size, size, null);

        // 이름표
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        String label = a.name;
        int tw = fm.stringWidth(label) + 10;
        int ty = feetY + 8;
        Color tag = a.npc ? new Color(255, 236, 179, 230) : a.name.equals(myName) ? new Color(255, 179, 200, 230) : new Color(255, 255, 255, 220);
        g.setColor(tag);
        g.fillRoundRect(feetX - tw / 2, ty, tw, 18, 9, 9);
        g.setColor(INK);
        g.drawString(label, feetX - tw / 2 + 5, ty + 13);
    }

    private void drawBubble(Graphics2D g, Avatar a, int scale) {
        g.setFont(getFont().deriveFont(13f));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(a.bubble, fm, 170);
        int lineH = fm.getHeight();
        int bw = 0;
        for (String l : lines) {
            bw = Math.max(bw, fm.stringWidth(l));
        }
        bw += 16;
        int bh = lineH * lines.size() + 10;
        int cx = (int) Math.round(a.x * scale);
        int top = Plaza.FEET_Y * scale - 56 * scale - bh - 10;
        int left = cx - bw / 2;

        g.setColor(new Color(255, 255, 255, 240));
        g.fillRoundRect(left, top, bw, bh, 14, 14);
        g.fillPolygon(new int[]{cx - 6, cx + 6, cx}, new int[]{top + bh - 1, top + bh - 1, top + bh + 8}, 3);
        g.setColor(new Color(0xE0B0C0));
        g.drawRoundRect(left, top, bw, bh, 14, 14);
        g.setColor(INK);
        int y = top + 5 + fm.getAscent();
        for (String l : lines) {
            g.drawString(l, left + 8, y);
            y += lineH;
        }
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            cur.append(c);
            if (fm.stringWidth(cur.toString()) > maxW) {
                cur.setLength(cur.length() - 1);
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** bg/plaza.png 가 있으면 그걸, 없으면 임시 광장 (하늘, 나무, 벤치, 길) */
    private void drawBackground(Graphics2D g, int s) {
        int mw = Plaza.MAP_W * s;
        int mh = Plaza.MAP_H * s;
        if (background != null) {
            g.drawImage(background, 0, 0, mw, mh, null);
            return;
        }
        int ground = (Plaza.FEET_Y - 14) * s;
        g.setColor(new Color(0xDFF3FF));
        g.fillRect(0, 0, mw, ground);
        // 구름
        g.setColor(Color.WHITE);
        int[][] clouds = {{30, 24}, {150, 14}, {270, 30}, {360, 18}};
        for (int[] c : clouds) {
            g.fillRoundRect(c[0] * s, c[1] * s, 34 * s, 9 * s, 9 * s, 9 * s);
            g.fillRoundRect((c[0] + 8) * s, (c[1] - 5) * s, 18 * s, 9 * s, 9 * s, 9 * s);
        }
        // 뒤쪽 잔디 언덕
        g.setColor(new Color(0xC8EFC8));
        g.fillRect(0, (Plaza.FEET_Y - 40) * s, mw, 26 * s);
        // 나무
        int[] trees = {20, 120, 230, 300, 385};
        for (int tx : trees) {
            g.setColor(new Color(0xB98A6A));
            g.fillRect((tx - 2) * s, (Plaza.FEET_Y - 40) * s, 4 * s, 18 * s);
            g.setColor(new Color(0x9ED89E));
            g.fillOval((tx - 12) * s, (Plaza.FEET_Y - 66) * s, 24 * s, 28 * s);
        }
        // 길
        g.setColor(new Color(0xFFE7D1));
        g.fillRect(0, ground, mw, mh - ground);
        g.setColor(new Color(0xF5D3B5));
        for (int x = 0; x < Plaza.MAP_W; x += 16) {
            g.fillRect(x * s, ground + 6 * s, 8 * s, s);
        }
        // 벤치
        int[] benches = {165, 255};
        for (int bx : benches) {
            g.setColor(new Color(0xD59C7A));
            g.fillRect(bx * s, (Plaza.FEET_Y - 22) * s, 26 * s, 3 * s);
            g.fillRect((bx + 2) * s, (Plaza.FEET_Y - 19) * s, 2 * s, 7 * s);
            g.fillRect((bx + 22) * s, (Plaza.FEET_Y - 19) * s, 2 * s, 7 * s);
        }
    }
}
