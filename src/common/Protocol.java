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

    // 청혼 (예민) : 다 자란 포프끼리 청혼 -> 성공하면 둘 다 지금 포프는 도감으로 가고 새 알을 키운다
    /** 클라이언트 -> 서버: /PROPOSE 상대닉네임 (NPC 이름이면 NPC 가 2초 뒤 대답, 20% 거절) */
    public static final String PROPOSE = "/PROPOSE";
    /** 서버 -> 광장: /PROPOSE_SAY 신청자 상대 대사  (신청자 포프 위에 청혼 대사 말풍선) */
    public static final String PROPOSE_SAY = "/PROPOSE_SAY";
    /** 서버 -> 상대: /PROPOSE_ASK 신청자  (상대 화면에서 2초 뒤 받아들일지 묻는 창) */
    public static final String PROPOSE_ASK = "/PROPOSE_ASK";
    /** 클라이언트(상대) -> 서버: /PROPOSE_ACCEPT 신청자 */
    public static final String PROPOSE_ACCEPT = "/PROPOSE_ACCEPT";
    /** 클라이언트(상대) -> 서버: /PROPOSE_REJECT 신청자 */
    public static final String PROPOSE_REJECT = "/PROPOSE_REJECT";
    /** 서버 -> 광장: /PROPOSE_ANSWER 대답한사람 신청자 YES|NO 대사  (대답한 포프 위에 말풍선) */
    public static final String PROPOSE_ANSWER = "/PROPOSE_ANSWER";
    /** 서버 -> 본인: /PROPOSE_FAIL 이유 */
    public static final String PROPOSE_FAIL = "/PROPOSE_FAIL";
    /** 서버 -> 본인: /EGG 상대 돌연변이수 유전자  (청혼 성공. 이 알이 새로 키울 포프, 바로 뒤에 /POP, /DEX) */
    public static final String EGG = "/EGG";
    /** 서버 -> 본인: /DEX 유전자|짝 유전자|짝 ...  (도감: 청혼으로 떠나보낸 포프들, 오래된 순) */
    public static final String DEX = "/DEX";

    // 정헌 담당 (귓속말은 /귓속말 또는 /WHISPER, 응답은 /WHISPER_FROM, /WHISPER_TO)
    public static final String WHISPER = "/WHISPER";
    public static final String FRIEND_REQ = "/FRIEND_REQ";
    public static final String FRIEND_OK = "/FRIEND_OK";
    public static final String DM = "/DM";
    public static final String EMOJI = "/EMOJI";

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
