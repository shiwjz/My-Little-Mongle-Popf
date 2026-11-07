package client;

import common.Gene;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 집 화면 (메인 창 가운데).
 * 1주차: 방 배경 + 알 하나. 알은 살짝 위아래로 흔들린다.
 * 2주차: setGene()으로 포프를 넘기면 알 대신 포프를 그린다 (부화 이후).
 */
public class HomePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color WALL = new Color(0xFFF5F8);
    private static final Color WALL_LINE = new Color(0xFFE3EC);
    private static final Color FLOOR = new Color(0xFFE1EA);
    private static final Color FLOOR_LINE = new Color(0xF8CFDB);
    private static final Color RUG = new Color(0xFFC9D8);
    private static final Color WINDOW = new Color(0xD8EEFF);
    private static final Color FRAME = new Color(0xE7B6C6);
    private static final Color INK = new Color(0x3A2E33);

    private final String owner;
    private final BufferedImage eggImage = makeEggImage();

    private Gene gene;               // null 이면 아직 알
    private BufferedImage popImage;  // gene 을 그린 그림 (캐시)

    private double t = 0;            // 흔들림 애니메이션 시간

    public HomePanel(String owner) {
        this.owner = owner;
        setPreferredSize(new Dimension(560, 560));

        // 약 30fps 로 살짝 흔들기
        new Timer(33, e -> {
            t += 0.08;
            repaint();
        }).start();
    }

    /** 부화 후 포프를 보여줄 때 사용. null 을 넘기면 다시 알 */
    public void setGene(Gene gene) {
        this.gene = gene;
        this.popImage = gene == null ? null : PopRenderer.render(gene, 256);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int floorY = (int) (h * 0.62);

        // 벽
        g.setColor(WALL);
        g.fillRect(0, 0, w, floorY);
        g.setColor(WALL_LINE);
        for (int x = 24; x < w; x += 48) {
            g.fillRect(x, 0, 2, floorY);
        }

        // 창문
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

        // 바닥
        g.setColor(FLOOR);
        g.fillRect(0, floorY, w, h - floorY);
        g.setColor(FLOOR_LINE);
        for (int y = floorY + 20; y < h; y += 28) {
            g.fillRect(0, y, w, 2);
        }

        // 러그
        int cx = w / 2;
        int rugW = Math.min(360, (int) (w * 0.6));
        int rugY = floorY + (h - floorY) / 3;
        g.setColor(RUG);
        g.fillOval(cx - rugW / 2, rugY - 34, rugW, 68);

        // 알 또는 포프
        int bob = (int) Math.round(Math.sin(t) * 4);
        BufferedImage sprite = (gene == null) ? eggImage : popImage;
        int size = gene == null ? 168 : 256;
        int sx = cx - size / 2;
        int sy = rugY - size + 10 + bob;

        // 그림자
        g.setColor(new Color(0, 0, 0, 30));
        int shadowW = (int) (size * 0.55) - bob * 2;
        g.fillOval(cx - shadowW / 2, rugY - 4, shadowW, 14);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (gene == null) {
            // 알은 좌우로도 아주 살짝 기울어진다
            double tilt = Math.sin(t * 0.7) * 0.05;
            Graphics2D eg = (Graphics2D) g.create();
            eg.rotate(tilt, cx, sy + size);
            eg.drawImage(sprite, sx, sy, size, size, null);
            eg.dispose();
        } else {
            g.drawImage(sprite, sx, sy, size, size, null);
        }

        // 집 이름
        g.setColor(INK);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString(owner + "의 집", 16, 26);
        g.setFont(getFont().deriveFont(12f));
        g.setColor(new Color(0x9A7F8A));
        g.drawString(gene == null ? "알을 돌봐주면 곧 부화해요" : "", 16, 46);

        g.dispose();
    }

    /**
     * 도트 느낌의 알 그림을 작은 격자(28x28)로 만든다. 그릴 때 크게 늘려서 파츠 그림과 느낌을 맞춘다.
     * 바깥선 = 알 안쪽 픽셀 중 옆에 바깥 픽셀이 있는 곳.
     */
    private static BufferedImage makeEggImage() {
        final int n = 28;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        boolean[][] inside = new boolean[n][n];

        double cx = 13.5, cy = 16.5, a = 9.5;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x - cx) / a;
                double b = (y < cy) ? 13.5 : 10.0; // 위쪽은 길쭉, 아래쪽은 통통
                double dy = (y - cy) / b;
                inside[y][x] = dx * dx + dy * dy <= 1.0;
            }
        }

        int outline = 0xFF3A2E33;
        int shell = 0xFFFFF6E3;
        int shellDark = 0xFFF3DDBA;
        int spot = 0xFFFFB3C9;
        int shine = 0xFFFFFFFF;

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!inside[y][x]) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == n - 1 || y == n - 1
                        || !inside[y - 1][x] || !inside[y + 1][x] || !inside[y][x - 1] || !inside[y][x + 1];
                int color = edge ? outline : shell;
                // 오른쪽 아래는 살짝 어둡게 (명암)
                if (!edge && (x - cx) + (y - cy) * 0.8 > 7.5) {
                    color = shellDark;
                }
                img.setRGB(x, y, color);
            }
        }

        // 분홍 점무늬
        int[][] spots = {{10, 12}, {11, 12}, {10, 13}, {17, 17}, {18, 17}, {17, 18}, {18, 18}, {12, 22}, {13, 22}, {19, 10}};
        for (int[] p : spots) {
            if (inside[p[1]][p[0]]) {
                img.setRGB(p[0], p[1], spot);
            }
        }
        // 반짝이
        int[][] shines = {{9, 7}, {9, 8}, {8, 9}, {8, 10}};
        for (int[] p : shines) {
            img.setRGB(p[0], p[1], shine);
        }
        return img;
    }
}
