package client;

import common.PartCatalog;
import common.Pop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 집 화면 (메인 창 가운데).
 *
 * - 배경: bg/home.png (200x192 도트 권장) 가 있으면 그걸 정수배로 키워서, 없으면 코드로 그린 방
 * - 포프: 서버에서 받은 Pop 의 단계(알/아기/성체)에 맞는 그림. 64 도트를 배경과 같은 배율로 키운다
 * - 부화·성장할 때: 알이 크게 흔들리다가 반짝이며 새 모습으로 바뀐다
 */
public class HomePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // 배경 그림 기준 크기 (도트)
    private static final int BG_W = 200;
    private static final int BG_H = 192;
    /** 배경 그림에서 발이 닿는 높이 (도트, 위에서부터) */
    private static final int BG_FEET_Y = 168;
    /** 스프라이트(64 도트) 안에서 발이 닿는 줄 */
    private static final int SPRITE_FEET_Y = 60;

    private static final Color WALL = new Color(0xFFF5F8);
    private static final Color WALL_LINE = new Color(0xFFE3EC);
    private static final Color FLOOR = new Color(0xFFE1EA);
    private static final Color FLOOR_LINE = new Color(0xF8CFDB);
    private static final Color RUG = new Color(0xFFC9D8);
    private static final Color WINDOW = new Color(0xD8EEFF);
    private static final Color FRAME = new Color(0xE7B6C6);
    private static final Color INK = new Color(0x3A2E33);

    private static final long SHAKE_MS = 1200;   // 흔들리는 시간
    private static final long SPARKLE_MS = 900;  // 반짝이는 시간

    private final String owner;
    private final BufferedImage background = loadBackground();

    private Pop pop;
    private BufferedImage sprite;      // 지금 보이는 그림 (64 도트 x16 = 1024)
    private BufferedImage nextSprite;  // 성장 연출 중에 바뀔 그림

    private long animStart = -1;       // 성장 연출 시작 시각 (-1 = 연출 없음)
    private double t = 0;              // 평소 흔들림 시간

    public HomePanel(String owner) {
        this.owner = owner;
        setPreferredSize(new Dimension(600, 500));
        new Timer(33, e -> {
            t += 0.08;
            repaint();
        }).start();
    }

    private static BufferedImage loadBackground() {
        File f = PartCatalog.file("bg", "home");
        if (!f.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 서버에서 /POP 을 받을 때마다 호출 (EDT 에서) */
    public void setPop(Pop pop) {
        this.pop = pop;
        BufferedImage img = PopRenderer.render(pop, PopRenderer.SIZE);
        if (isShaking()) {
            nextSprite = img;   // 흔들기가 끝나면 바뀐다
        } else {
            sprite = img;
        }
        repaint();
    }

    /** 서버에서 /GROW 를 받으면 호출 (/POP 보다 먼저 온다) */
    public void playGrow() {
        animStart = System.currentTimeMillis();
        nextSprite = null;
    }

    private boolean isShaking() {
        return animStart >= 0 && System.currentTimeMillis() - animStart < SHAKE_MS;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth();
        int h = getHeight();

        // ---- 배경 + 배율 정하기 ----
        int scale;
        int feetX = w / 2;
        int feetY;
        if (background != null) {
            scale = Math.max(1, Math.min(w / background.getWidth(), h / background.getHeight()));
            int bw = background.getWidth() * scale;
            int bh = background.getHeight() * scale;
            int bx = (w - bw) / 2;
            int by = (h - bh) / 2;
            g.setColor(WALL);
            g.fillRect(0, 0, w, h);
            g.drawImage(background, bx, by, bw, bh, null);
            feetY = by + BG_FEET_Y * bh / BG_H;
        } else {
            scale = Math.max(1, Math.min(3, Math.min(w / BG_W, h / BG_H)));
            feetY = drawDefaultRoom(g, w, h);
        }

        // ---- 포프 ----
        if (sprite == null) {
            g.setColor(INK);
            g.setFont(getFont().deriveFont(14f));
            g.drawString("포프를 불러오는 중...", feetX - 60, feetY - 40);
        } else {
            drawPop(g, feetX, feetY, 64 * scale);
        }

        // ---- 글씨 ----
        g.setColor(INK);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString(owner + "의 집", 16, 26);
        if (pop != null) {
            g.setFont(getFont().deriveFont(12f));
            g.setColor(new Color(0x9A7F8A));
            g.drawString(subtitle(), 16, 46);
        }
        g.dispose();
    }

    private String subtitle() {
        int left = (pop.expGoal() - pop.exp + common.GrowthRule.EXP_PER_CARE - 1) / common.GrowthRule.EXP_PER_CARE;
        switch (pop.stage) {
            case EGG:
                return "알을 돌봐주세요. 부화까지 " + left + "번!";
            case BABY:
                return "아기 포프예요. 다 자라기까지 " + left + "번!";
            default:
                return "다 자란 포프예요. 이제 교배할 수 있어요.";
        }
    }

    private void drawPop(Graphics2D g, int feetX, int feetY, int size) {
        long now = System.currentTimeMillis();
        long elapsed = animStart >= 0 ? now - animStart : -1;

        // 흔들기가 끝나면 새 그림으로 바꾸고 반짝이 시작
        if (elapsed >= SHAKE_MS && nextSprite != null) {
            sprite = nextSprite;
            nextSprite = null;
        }
        if (elapsed >= SHAKE_MS + SPARKLE_MS) {
            animStart = -1;
            elapsed = -1;
        }

        int bob = (int) Math.round(Math.sin(t) * 3);
        double tilt = 0;
        int shakeX = 0;
        if (elapsed >= 0 && elapsed < SHAKE_MS) {
            // 점점 세게 흔들림
            double power = 0.3 + 0.7 * elapsed / (double) SHAKE_MS;
            tilt = Math.sin(elapsed * 0.05) * 0.18 * power;
            shakeX = (int) Math.round(Math.sin(elapsed * 0.09) * 6 * power);
            bob = 0;
        } else if (pop != null && pop.stage == Pop.Stage.EGG) {
            tilt = Math.sin(t * 0.7) * 0.05; // 알은 평소에도 살짝 기우뚱
        }

        int sx = feetX - size / 2 + shakeX;
        int sy = feetY - SPRITE_FEET_Y * size / 64 + bob;

        // 그림자
        g.setColor(new Color(0, 0, 0, 30));
        int shadowW = size / 2 - bob * 2;
        g.fillOval(feetX - shadowW / 2, feetY - 6, shadowW, 12);

        Graphics2D sg = (Graphics2D) g.create();
        sg.rotate(tilt, feetX, feetY);
        sg.drawImage(sprite, sx, sy, size, size, null);
        sg.dispose();

        // 반짝이
        if (elapsed >= SHAKE_MS) {
            double p = (elapsed - SHAKE_MS) / (double) SPARKLE_MS; // 0 -> 1
            int cy = feetY - size / 3;
            g.setColor(new Color(255, 255, 255, (int) (200 * (1 - p))));
            int flash = (int) (size * (0.4 + p));
            g.fillOval(feetX - flash / 2, cy - flash / 2, flash, flash);
            g.setColor(new Color(255, 200, 220, (int) (255 * (1 - p))));
            for (int i = 0; i < 10; i++) {
                double ang = i * Math.PI / 5;
                int r = (int) (size * 0.25 + size * 0.45 * p);
                int px = feetX + (int) (Math.cos(ang) * r);
                int py = cy + (int) (Math.sin(ang) * r);
                int s = Math.max(2, size / 24);
                g.fillRect(px - s / 2, py - s * 2, s, s * 4);
                g.fillRect(px - s * 2, py - s / 2, s * 4, s);
            }
        }
    }

    /** bg/home.png 가 없을 때 쓰는 기본 방. 발 위치(y)를 돌려준다 */
    private int drawDefaultRoom(Graphics2D g, int w, int h) {
        int floorY = (int) (h * 0.62);

        g.setColor(WALL);
        g.fillRect(0, 0, w, floorY);
        g.setColor(WALL_LINE);
        for (int x = 24; x < w; x += 48) {
            g.fillRect(x, 0, 2, floorY);
        }

        int winW = Math.min(170, w / 4);
        int winH = (int) (winW * 0.8);
        int winX = w / 8;
        int winY = floorY / 5;
        g.setColor(FRAME);
        g.fillRoundRect(winX - 8, winY - 8, winW + 16, winH + 16, 16, 16);
        g.setColor(WINDOW);
        g.fillRect(winX, winY, winW, winH);
        g.setColor(FRAME);
        g.fillRect(winX + winW / 2 - 3, winY, 6, winH);
        g.fillRect(winX, winY + winH / 2 - 3, winW, 6);

        g.setColor(FLOOR);
        g.fillRect(0, floorY, w, h - floorY);
        g.setColor(FLOOR_LINE);
        for (int y = floorY + 20; y < h; y += 28) {
            g.fillRect(0, y, w, 2);
        }

        int rugW = Math.min(360, (int) (w * 0.6));
        int rugY = floorY + (h - floorY) / 2;
        g.setColor(RUG);
        g.fillOval(w / 2 - rugW / 2, rugY - 30, rugW, 60);
        return rugY;
    }
}
