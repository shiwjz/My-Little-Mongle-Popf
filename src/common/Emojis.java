package common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 이모티콘 코드 -> 표시할 글자. 서버는 검증, 클라이언트는 표시에 쓴다. */
public final class Emojis {

    private static final Map<String, String> MAP = new LinkedHashMap<>();

    static {
        MAP.put("heart", "\u2764\uFE0F");
        MAP.put("smile", "\uD83D\uDE04");
        MAP.put("cry", "\uD83D\uDE2D");
        MAP.put("angry", "\uD83D\uDE21");
        MAP.put("like", "\uD83D\uDC4D");
        MAP.put("star", "\u2B50");
    }

    private Emojis() {
    }

    /** 없는 코드면 null */
    public static String get(String code) {
        return MAP.get(code);
    }

    public static Set<String> codes() {
        return MAP.keySet();
    }
}