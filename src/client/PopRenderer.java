package client;

import common.Gene;

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
 * 파츠 그림은 모두 1024x1024 크기이고 같은 위치에 맞춰져 있어서, (0,0)에 순서대로 겹치기만 하면 된다.
 * 그림 안의 "마커 색"(빨강/노랑/파랑/초록/마젠타/하늘)을 유전자의 색으로 바꿔서 색을 입힌다.
 * (웹 버전 index.html 의 recolorImage 를 Java로 옮긴 것)
 */
public class PopRenderer {

    public static final int SIZE = 1024;

    /** 그리는 순서 (아래 -> 위) */
    private static final String[] LAYER_ORDER = {"tail", "body", "eye", "mouth", "hair", "hat", "cloth", "acc"};

    // 마커 색
    private static final int[] BASE = {255, 0, 0};
    private static final int[] BASE_BRIGHT = {255, 255, 0};
    private static final int[] BASE_DARK = {0, 0, 255};
    private static final int[] POINT = {0, 255, 0};
    private static final int[] POINT_BRIGHT = {255, 0, 255};
    private static final int[] POINT_DARK = {0, 255, 255};

    private static final int TONE = 40; // 밝은/어두운 명암 차이

    /** 원본 파츠 그림 캐시 (파일을 매번 다시 읽지 않도록) */
    private static final Map<String, BufferedImage> RAW_CACHE = new HashMap<>();

    private static File baseDir = findBaseDir();

    /** body/ eye/ ... 폴더가 있는 위치를 찾는다. 프로젝트 루트나 src 안에서 실행해도 되게. */
    private static File findBaseDir() {
        String[] candidates = {".", "..", "../.."};
        for (String c : candidates) {
            File dir = new File(c);
            if (new File(dir, "body").isDirectory()) {
                return dir;
            }
        }
        return new File(".");
    }

    public static void setBaseDir(File dir) {
        baseDir = dir;
    }

    /** 유전자대로 포프를 그려서 size x size 이미지로 돌려준다. */
    public static BufferedImage render(Gene gene, int size) {
        BufferedImage canvas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();

        for (String key : LAYER_ORDER) {
            if (key.equals("acc")) {
                for (String name : gene.getAccs()) {
                    drawPart(g, key, name, gene);
                }
            } else {
                String name = gene.get(key);
                if (name != null) {
                    drawPart(g, key, name, gene);
                }
            }
        }
        g.dispose();

        if (size == SIZE) {
            return canvas;
        }
        return scale(canvas, size);
    }

    private static void drawPart(Graphics2D g, String key, String name, Gene gene) {
        BufferedImage raw = loadRaw(key, name);
        if (raw == null) {
            return;
        }
        BufferedImage colored = recolor(raw, gene.baseColorOf(key), gene.pointColorOf(key));
        g.drawImage(colored, 0, 0, null);
    }

    private static BufferedImage loadRaw(String key, String name) {
        String path = key + "/" + name + ".png";
        if (RAW_CACHE.containsKey(path)) {
            return RAW_CACHE.get(path);
        }
        BufferedImage img = null;
        try {
            img = ImageIO.read(new File(baseDir, path));
        } catch (IOException e) {
            System.out.println("[PopRenderer] 그림을 못 찾음: " + path);
        }
        RAW_CACHE.put(path, img);
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

    /** 도트 그림이 뭉개지지 않게 '가장 가까운 픽셀' 방식으로 줄인다 */
    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
