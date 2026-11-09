package client;

import common.Gene;
import common.PartCatalog;
import common.Pop;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 유전자(Gene)를 보고 파츠 PNG를 색칠해서 한 장으로 겹쳐 그린다.
 *
 * - 파츠 그림은 64x64 도트. 파일은 64x64 그대로든 16배 키운 1024x1024든 둘 다 읽는다.
 * - 그림 안의 "마커 색"(빨강/노랑/파랑/초록/마젠타/하늘)을 유전자의 테마 색으로 바꿔서 색을 입힌다.
 * - 단계별 그림
 *     알   : egg/egg.png 가 있으면 그걸 (마커 색으로 칠했으면 몸통 색으로 바뀜), 없으면 코드로 그린 기본 알
 *     아기 : BABY_LAYERS 의 파츠만. baby_body/같은이름.png 처럼 baby_ 폴더에 그림이 있으면 그걸 쓰고,
 *            없으면 성체 그림을 작게 줄여서 임시로 보여준다
 *     성체 : 모든 파츠
 */
public class PopRenderer {

    /** 내부 캔버스 크기 (64 도트 x 16배) */
    public static final int SIZE = 1024;
    private static final int DOT = SIZE / 64;

    /** 발이 닿는 줄 (64 도트 기준 60번째 줄). 아기 임시 그림을 줄일 때 기준 */
    private static final int FEET_Y = 60 * DOT;

    /** 그리는 순서 (아래 -> 위) */
    private static final String[] LAYER_ORDER = {"tail", "body", "eye", "mouth", "hair", "hat", "cloth", "acc"};

    /** 아기 때 보여줄 파츠. 나중에 바꾸고 싶으면 여기만 수정 */
    private static final String[] BABY_LAYERS = {"body", "eye"};
    /** 아기 전용 몸통 그림이 없을 때 성체 그림을 줄이는 비율 */
    private static final double BABY_PLACEHOLDER_SCALE = 0.7;

    // 마커 색
    private static final int[] BASE = {255, 0, 0};
    private static final int[] BASE_BRIGHT = {255, 255, 0};
    private static final int[] BASE_DARK = {0, 0, 255};
    private static final int[] POINT = {0, 255, 0};
    private static final int[] POINT_BRIGHT = {255, 0, 255};
    private static final int[] POINT_DARK = {0, 255, 255};

    private static final int TONE = 40; // 밝은/어두운 명암 차이

    /** 원본 그림 캐시 (파일을 매번 다시 읽지 않도록). 파일이 없으면 null 이 들어간다 */
    private static final Map<String, BufferedImage> RAW_CACHE = new HashMap<>();

    /** 파츠 파일을 바꾼 뒤 다시 읽게 하고 싶을 때 */
    public static synchronized void clearCache() {
        RAW_CACHE.clear();
        PartCatalog.reload();
    }

    // ------------------------------------------------------------------
    // 단계별 그리기
    // ------------------------------------------------------------------

    /** 포프의 지금 단계에 맞는 그림 */
    public static BufferedImage render(Pop pop, int size) {
        switch (pop.stage) {
            case EGG:
                return renderEgg(pop.gene, size);
            case BABY:
                return renderBaby(pop.gene, size);
            default:
                return render(pop.gene, size);
        }
    }

    /** 성체: 모든 파츠 */
    public static BufferedImage render(Gene gene, int size) {
        BufferedImage canvas = newCanvas();
        Graphics2D g = canvas.createGraphics();
        for (String key : LAYER_ORDER) {
            if (key.equals("acc")) {
                for (String name : gene.getAccs()) {
                    drawPart(g, key, key, name, gene);
                }
            } else if (gene.get(key) != null) {
                drawPart(g, key, key, gene.get(key), gene);
            }
        }
        g.dispose();
        return scale(canvas, size);
    }

    /** 아기: BABY_LAYERS 파츠만, baby_ 폴더 그림 우선 */
    public static BufferedImage renderBaby(Gene gene, int size) {
        BufferedImage canvas = newCanvas();
        Graphics2D g = canvas.createGraphics();
        for (String key : LAYER_ORDER) {
            if (!contains(BABY_LAYERS, key) || key.equals("acc") || gene.get(key) == null) {
                continue;
            }
            String name = gene.get(key);
            String folder = PartCatalog.exists("baby_" + key, name) ? "baby_" + key : key;
            drawPart(g, folder, key, name, gene);
        }
        g.dispose();

        boolean hasBabyBody = PartCatalog.exists("baby_body", gene.get("body"));
        if (!hasBabyBody) {
            canvas = shrinkToFeet(canvas, BABY_PLACEHOLDER_SCALE);
        }
        return scale(canvas, size);
    }

    /** 알: egg/egg.png 가 있으면 그걸, 없으면 기본 알 */
    public static BufferedImage renderEgg(Gene gene, int size) {
        BufferedImage canvas = newCanvas();
        Graphics2D g = canvas.createGraphics();
        if (PartCatalog.exists("egg", "egg")) {
            drawPart(g, "egg", "body", "egg", gene); // 마커 색으로 칠했으면 몸통 색을 따라감
        } else {
            g.drawImage(defaultEgg(), 0, 0, null);
        }
        g.dispose();
        return scale(canvas, size);
    }

    // ------------------------------------------------------------------
    // 파츠 그리기
    // ------------------------------------------------------------------

    /**
     * @param folder 그림을 읽을 폴더 (body, baby_body, egg ...)
     * @param colorKey 색을 정할 때 쓰는 파츠 종류 (body, eye ...)
     */
    private static void drawPart(Graphics2D g, String folder, String colorKey, String name, Gene gene) {
        BufferedImage raw = loadRaw(folder, name);
        if (raw == null) {
            return;
        }
        BufferedImage colored = recolor(raw, gene.baseColorOf(colorKey), gene.pointColorOf(colorKey));
        g.drawImage(colored, 0, 0, null);
    }

    /** 그림을 읽어서 항상 1024x1024 로 맞춘다 (64x64 파일이면 16배로 키움) */
    private static synchronized BufferedImage loadRaw(String folder, String name) {
        String key = folder + "/" + name;
        if (RAW_CACHE.containsKey(key)) {
            return RAW_CACHE.get(key);
        }
        BufferedImage img = null;
        File f = PartCatalog.file(folder, name);
        try {
            if (f.isFile()) {
                img = ImageIO.read(f);
                if (img != null && (img.getWidth() != SIZE || img.getHeight() != SIZE)) {
                    img = scale(toArgb(img), SIZE);
                }
            } else {
                System.out.println("[PopRenderer] 그림 없음: " + folder + "/" + name + ".png");
            }
        } catch (IOException e) {
            System.out.println("[PopRenderer] 그림을 못 읽음: " + f + " (" + e.getMessage() + ")");
        }
        RAW_CACHE.put(key, img);
        return img;
    }

    /** 마커 색을 실제 색으로 바꾼다. 검정 외곽선과 흰자는 그대로 둔다. */
    private static BufferedImage recolor(BufferedImage src, int baseRgb, int pointRgb) {
        int[][] base = tones(baseRgb);
        int[][] point = tones(pointRgb);

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        for (int i = 0; i < px.length; i++) {
            int argb = px[i];
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int gr = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            if (a < 10) {
                continue;
            }
            if (r < 20 && gr < 20 && b < 20) {
                continue; // 검정 외곽선
            }
            if (r > 240 && gr > 240 && b > 240) {
                continue; // 흰색
            }

            int[] nc = null;
            if (match(r, gr, b, BASE)) nc = base[0];
            else if (match(r, gr, b, BASE_BRIGHT)) nc = base[1];
            else if (match(r, gr, b, BASE_DARK)) nc = base[2];
            else if (match(r, gr, b, POINT)) nc = point[0];
            else if (match(r, gr, b, POINT_BRIGHT)) nc = point[1];
            else if (match(r, gr, b, POINT_DARK)) nc = point[2];

            if (nc != null) {
                px[i] = (0xFF << 24) | (nc[0] << 16) | (nc[1] << 8) | nc[2];
            }
        }
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static boolean match(int r, int g, int b, int[] t) {
        return Math.abs(r - t[0]) < 50 && Math.abs(g - t[1]) < 50 && Math.abs(b - t[2]) < 50;
    }

    /** 기본색에서 기본/밝은/어두운 세 가지 명암을 만든다 */
    private static int[][] tones(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return new int[][]{
                {r, g, b},
                {clamp(r + TONE), clamp(g + TONE), clamp(b + TONE)},
                {clamp(r - TONE), clamp(g - TONE), clamp(b - TONE)}
        };
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------
    // 이미지 유틸
    // ------------------------------------------------------------------

    private static BufferedImage newCanvas() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** 도트 그림이 뭉개지지 않게 '가장 가까운 픽셀' 방식으로 크기를 바꾼다 */
    static BufferedImage scale(BufferedImage src, int size) {
        if (src.getWidth() == size && src.getHeight() == size) {
            return src;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** 발 위치는 그대로 두고 가운데로 줄인다 (아기 임시 그림용) */
    private static BufferedImage shrinkToFeet(BufferedImage src, double s) {
        BufferedImage out = newCanvas();
        int w = (int) Math.round(SIZE * s / DOT) * DOT; // 도트 칸에 맞춰서
        int x = (SIZE - w) / 2;
        int y = (int) Math.round(FEET_Y * (1 - s) / DOT) * DOT;
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, x, y, w, w, null);
        g.dispose();
        return out;
    }

    private static boolean contains(String[] arr, String v) {
        for (String a : arr) {
            if (a.equals(v)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 기본 알 (egg/egg.png 가 없을 때)
    // ------------------------------------------------------------------

    private static BufferedImage defaultEggCache;

    /** 64x64 도트 캔버스의 아래쪽 가운데에 알을 그리고 1024 로 키운다 */
    private static synchronized BufferedImage defaultEgg() {
        if (defaultEggCache != null) {
            return defaultEggCache;
        }
        final int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        boolean[][] inside = new boolean[n][n];

        // 가로 20, 세로 26 정도의 알. 아래 끝이 발 줄(60)에 닿게
        double cx = 31.5, cy = 47.0, a = 10.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x - cx) / a;
                double bb = (y < cy) ? 14.0 : 12.5; // 위쪽은 길쭉, 아래쪽은 통통
                double dy = (y - cy) / bb;
                inside[y][x] = y <= 59 && dx * dx + dy * dy <= 1.0;
            }
        }

        int outline = 0xFF3A2E33;
        int shell = 0xFFFFF6E3;
        int shellDark = 0xFFF3DDBA;
        int spot = 0xFFFFB3C9;
        int shine = 0xFFFFFFFF;

        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                if (!inside[y][x]) {
                    continue;
                }
                boolean edge = !inside[y - 1][x] || !inside[y + 1][x] || !inside[y][x - 1] || !inside[y][x + 1];
                int color = edge ? outline : shell;
                if (!edge && (x - cx) + (y - cy) * 0.8 > 8.0) {
                    color = shellDark; // 오른쪽 아래 명암
                }
                img.setRGB(x, y, color);
            }
        }
        int[][] spots = {{27, 42}, {28, 42}, {27, 43}, {35, 48}, {36, 48}, {35, 49}, {36, 49}, {29, 53}, {30, 53}, {37, 40}};
        for (int[] p : spots) {
            if (inside[p[1]][p[0]]) {
                img.setRGB(p[0], p[1], spot);
            }
        }
        int[][] shines = {{26, 37}, {26, 38}, {25, 39}, {25, 40}};
        for (int[] p : shines) {
            img.setRGB(p[0], p[1], shine);
        }
        defaultEggCache = scale(img, SIZE);
        return defaultEggCache;
    }
}
