package common;

/**
 * 서버와 클라이언트가 같이 쓰는 프로토콜 상수.
 * 형식은 "/명령 값 값" 이고, 띄어쓰기가 들어갈 수 있는 값(채팅 내용 등)은 항상 맨 뒤에 둔다.
 *
 * 1주차에 쓰는 것
 *   클라이언트 -> 서버 : /LOGIN 닉네임, /CHAT 내용, /BYE
 *   서버 -> 클라이언트 : /LOGIN_OK, /LOGIN_FAIL 이유, /ENTER 닉네임, /LEAVE 닉네임, /CHAT 닉네임: 내용
 */
public final class Protocol {

    private Protocol() {
    }

    public static final int PORT = 9999;
    public static final String DEFAULT_HOST = "localhost";

    // 1주차
    public static final String LOGIN = "/LOGIN";
    public static final String LOGIN_OK = "/LOGIN_OK";
    public static final String LOGIN_FAIL = "/LOGIN_FAIL";
    public static final String ENTER = "/ENTER";
    public static final String LEAVE = "/LEAVE";
    public static final String CHAT = "/CHAT";
    public static final String BYE = "/BYE";

    // 2주차: 포프 육성
    /** 서버 -> 본인: /POP 단계 경험치 배고픔 청결 행복 유전자 */
    public static final String POP = "/POP";
    /** 클라이언트 -> 서버: /CARE FEED|WASH|PET */
    public static final String CARE = "/CARE";
    /** 서버 -> 본인: /GROW BABY|ADULT (부화·성장했을 때, /POP 보다 먼저) */
    public static final String GROW = "/GROW";

    // 광장 (예민)
    /** 클라이언트 -> 서버: 광장 입장 */
    public static final String PLAZA = "/PLAZA";
    /** 서버 -> 본인: /PLAZA_OK 내x  (이어서 이미 있던 사람들의 /PLAZA_IN 이 온다) */
    public static final String PLAZA_OK = "/PLAZA_OK";
    /** 서버 -> 광장: /PLAZA_IN 닉네임 x 방향 단계 경험치 배고픔 청결 행복 유전자 */
    public static final String PLAZA_IN = "/PLAZA_IN";
    /** 서버 -> 광장: /PLAZA_OUT 닉네임 */
    public static final String PLAZA_OUT = "/PLAZA_OUT";
    /** 클라이언트 -> 서버: 집으로 */
    public static final String HOME = "/HOME";
    /** 서버 -> 본인: 집에 도착 */
    public static final String HOME_OK = "/HOME_OK";
    /** 클라이언트 -> 서버: /MOVE x 방향(L|R),  서버 -> 광장: /MOVE 닉네임 x 방향 */
    public static final String MOVE = "/MOVE";

    // 정헌 담당으로 쓸 예정 (이름만 먼저 정해둠)
    public static final String WHISPER = "/WHISPER";
    public static final String FRIEND_REQ = "/FRIEND_REQ";
    public static final String FRIEND_OK = "/FRIEND_OK";
    public static final String DM = "/DM";
    public static final String EMOJI = "/EMOJI";
    public static final String BREED_REQ = "/BREED_REQ";
    public static final String BREED_ACCEPT = "/BREED_ACCEPT";
    public static final String BREED_REJECT = "/BREED_REJECT";
    public static final String EGG = "/EGG";

    /** "/CHAT 안녕" 처럼 명령 뒤에 오는 값을 꺼낸다. 값이 없으면 빈 문자열. */
    public static String body(String message, String command) {
        if (message.length() <= command.length()) {
            return "";
        }
        return message.substring(command.length()).trim();
    }

    /** message 가 정확히 command 이거나 "command 값" 형태인지 확인한다. */
    public static boolean is(String message, String command) {
        return message.equals(command) || message.startsWith(command + " ");
    }
}
