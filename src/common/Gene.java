package common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 포프 한 마리의 유전자.
 *
 * 네트워크로는 그림 대신 이 문자열만 주고받는다. 예)
 *   body=slime_body;eye=anime_eye;mouth=kitty_mouth;hair=-;hat=-;cloth=-;tail=-;acc=glasses_acc;color=FFB3C1,B3DCFF,FFF0B3,D2B3FF
 *
 * - 값이 "-" 이면 그 파츠는 없음
 * - acc(장신구)는 여러 개일 수 있어서 "+"로 잇는다 (예: acc=glasses_acc+touch_acc)
 * - color 는 테마 색 4개 (16진수 RGB). 파츠마다 이 중 두 색을 기본색/포인트색으로 쓴다
 *
 * 문자열 안에 띄어쓰기가 없어서 프로토콜 값으로 그대로 넣을 수 있다.
 */
public class Gene {

    /** 파츠 종류 (그리는 순서와는 별개). 귀는 아직 정하지 않아서 뺐다. */
    public static final String[] PART_KEYS = {"body", "eye", "mouth", "hair", "hat", "cloth", "tail", "acc"};

    /** 파츠별로 고를 수 있는 그림 이름 (폴더/파일 이름과 같음) */
    public static final Map<String, String[]> PARTS = new LinkedHashMap<>();

    static {
        PARTS.put("body", new String[]{"devil_body", "human_body", "slime_body"});
        PARTS.put("eye", new String[]{"anime2_eye", "anime_eye", "bean_eye", "human_eye", "sleep_eye"});
        PARTS.put("mouth", new String[]{"1_mouth", "basicsmile_mouth", "kitty_mouth"});
        PARTS.put("hair", new String[]{"basic_hair", "twinanime_hair"});
        PARTS.put("hat", new String[]{"pumkin_hat"});
        PARTS.put("cloth", new String[]{"libon_cloth"});
        PARTS.put("tail", new String[]{"butterfly_tail"});
        PARTS.put("acc", new String[]{"eyepatch_acc", "glasses_acc", "touch_acc"});
    }

    /** 처음 생성할 때(그리고 돌연변이 때) 파츠가 등장할 확률. 몸통·눈은 100%. */
    public static double appearChance(String key) {
        switch (key) {
            case "body":
            case "eye":
                return 1.0;
            case "mouth":
                return 0.4;
            default:
                return 0.1; // 머리, 모자, 옷, 꼬리, 장신구
        }
    }

    /** 파스텔 팔레트 (웹 버전과 같은 12색) */
    public static final int[] PASTEL_PALETTE = {
            0xFFB3C1, 0xFFD2B3, 0xFFF0B3, 0xB3F0C8, 0xB3DCFF, 0xD2B3FF,
            0xFFB3E6, 0xB3FFF0, 0xFFDCC8, 0xDCC8FF, 0xC8F0DC, 0xFFC8D2
    };

    private static final String NONE = "-";

    /** acc 를 제외한 파츠: key -> 그림 이름 (없으면 null) */
    private final Map<String, String> parts = new LinkedHashMap<>();
    /** 장신구는 여러 개 가능 */
    private final List<String> accs = new ArrayList<>();
    /** 테마 색 4개 (0xRRGGBB) */
    private final int[] colors = new int[4];

    public Gene() {
        for (String key : PART_KEYS) {
            if (!key.equals("acc")) {
                parts.put(key, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // 랜덤 생성
    // ------------------------------------------------------------------

    /** 최초 접속 때 지급하는 알의 랜덤 유전자 */
    public static Gene random(Random rnd) {
        Gene g = new Gene();
        for (String key : PART_KEYS) {
            String[] options = PARTS.get(key);
            if (key.equals("acc")) {
                // 장신구는 종류마다 따로 10% 확률
                for (String name : options) {
                    if (rnd.nextDouble() < appearChance("acc")) {
                        g.accs.add(name);
                    }
                }
            } else if (rnd.nextDouble() < appearChance(key)) {
                g.parts.put(key, options[rnd.nextInt(options.length)]);
            }
        }
        g.randomColors(rnd);
        return g;
    }

    /** 팔레트 12색 중 겹치지 않게 4색을 골라 테마로 쓴다 */
    public void randomColors(Random rnd) {
        List<Integer> pool = new ArrayList<>();
        for (int c : PASTEL_PALETTE) {
            pool.add(c);
        }
        Collections.shuffle(pool, rnd);
        for (int i = 0; i < 4; i++) {
            colors[i] = pool.get(i);
        }
    }

    // ------------------------------------------------------------------
    // 문자열 <-> 유전자
    // ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String key : PART_KEYS) {
            sb.append(key).append('=');
            if (key.equals("acc")) {
                sb.append(accs.isEmpty() ? NONE : String.join("+", accs));
            } else {
                String v = parts.get(key);
                sb.append(v == null ? NONE : v);
            }
            sb.append(';');
        }
        sb.append("color=");
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format("%06X", colors[i]));
        }
        return sb.toString();
    }

    /** toString() 으로 만든 문자열을 다시 유전자로. 모르는 파츠 이름은 무시(없음 처리)한다. */
    public static Gene parse(String text) {
        Gene g = new Gene();
        for (String pair : text.trim().split(";")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();

            if (key.equals("color")) {
                String[] hex = value.split(",");
                for (int i = 0; i < 4 && i < hex.length; i++) {
                    try {
                        g.colors[i] = Integer.parseInt(hex[i].trim(), 16) & 0xFFFFFF;
                    } catch (NumberFormatException ignored) {
                        g.colors[i] = PASTEL_PALETTE[i];
                    }
                }
            } else if (key.equals("acc")) {
                if (!value.equals(NONE)) {
                    for (String name : value.split("\\+")) {
                        if (isValid("acc", name)) {
                            g.accs.add(name);
                        }
                    }
                }
            } else if (g.parts.containsKey(key)) {
                g.parts.put(key, isValid(key, value) ? value : null);
            }
        }
        return g;
    }

    private static boolean isValid(String key, String name) {
        String[] options = PARTS.get(key);
        if (options == null) {
            return false;
        }
        for (String o : options) {
            if (o.equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    /** 파츠 그림 이름. 없으면 null. acc 는 getAccs() 사용 */
    public String get(String key) {
        return parts.get(key);
    }

    public List<String> getAccs() {
        return Collections.unmodifiableList(accs);
    }

    public int[] getColors() {
        return colors.clone();
    }

    /**
     * 파츠의 기본색. 웹 버전처럼 파츠 순서(i)에 따라 테마 색을 돌려가며 쓴다.
     * 포인트색은 그 옆 색.
     */
    public int baseColorOf(String key) {
        return colors[indexOf(key) % 4];
    }

    public int pointColorOf(String key) {
        return colors[(indexOf(key) + 1) % 4];
    }

    private static int indexOf(String key) {
        for (int i = 0; i < PART_KEYS.length; i++) {
            if (PART_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }
}
