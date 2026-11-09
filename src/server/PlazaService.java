package server;

import common.Plaza;
import common.Pop;
import common.Protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 광장 위치 관리 (예민 담당).
 *
 * 서버는 사용자마다 지금 "집"에 있는지 "광장"에 있는지 기억한다. 로그인하면 집에서 시작한다.
 *   /PLAZA        -> 광장 입장. 본인에게 /PLAZA_OK x + 이미 있던 사람들의 /PLAZA_IN, 다른 사람들에게 내 /PLAZA_IN
 *   /HOME         -> 집으로. 본인에게 /HOME_OK, 광장 사람들에게 /PLAZA_OUT
 *   /MOVE x 방향  -> 광장 안에서 이동. 광장의 다른 사람들에게 /MOVE 닉네임 x 방향
 *
 * 정헌이 채팅에서 쓸 수 있게 isInPlaza(), broadcastToPlaza() 를 열어둠.
 */
public final class PlazaService {

    private PlazaService() {
    }

    private static final class Member {
        final ClientHandler client;
        int x;
        String dir = "R";

        Member(ClientHandler client, int x) {
            this.client = client;
            this.x = x;
        }
    }

    /** 지금 광장에 있는 사람들 (들어온 순서) */
    private static final Map<String, Member> PLAZA = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();

    // ------------------------------------------------------------------
    // 정헌이 쓸 것
    // ------------------------------------------------------------------

    /** 이 사람이 지금 광장에 있는지 */
    public static synchronized boolean isInPlaza(String nickname) {
        return PLAZA.containsKey(nickname);
    }

    /** 광장에 있는 사람 모두에게 보내기 (전체 채팅을 광장에만 보낼 때 사용) */
    public static synchronized void broadcastToPlaza(String message) {
        for (Member m : PLAZA.values()) {
            m.client.sendMessage(message);
        }
    }

    /** 광장에 있는 사람 닉네임 목록 */
    public static synchronized List<String> plazaMembers() {
        return new ArrayList<>(PLAZA.keySet());
    }

    // ------------------------------------------------------------------
    // 입장 / 퇴장 / 이동
    // ------------------------------------------------------------------

    public static synchronized void enter(ClientHandler client, String nickname) {
        Member me = PLAZA.get(nickname);
        if (me == null) {
            // 가운데 근처에서 살짝 흩어져서 시작
            me = new Member(client, Plaza.clampX(Plaza.START_X + RANDOM.nextInt(81) - 40));
        }

        client.sendMessage(Protocol.PLAZA_OK + " " + me.x);
        // 이미 있던 사람들 알려주기
        for (Map.Entry<String, Member> e : PLAZA.entrySet()) {
            if (!e.getKey().equals(nickname)) {
                client.sendMessage(inLine(e.getKey(), e.getValue()));
            }
        }
        boolean isNew = !PLAZA.containsKey(nickname);
        PLAZA.put(nickname, me);
        if (isNew) {
            sendToOthers(nickname, inLine(nickname, me));
            System.out.println("[광장] " + nickname + " 입장 (" + PLAZA.size() + "명)");
        }
    }

    public static synchronized void leave(ClientHandler client, String nickname) {
        if (PLAZA.remove(nickname) != null) {
            sendToOthers(nickname, Protocol.PLAZA_OUT + " " + nickname);
            System.out.println("[광장] " + nickname + " 퇴장 (" + PLAZA.size() + "명)");
        }
        client.sendMessage(Protocol.HOME_OK);
    }

    /** "/MOVE x 방향" 의 값 부분 */
    public static synchronized void move(String nickname, String body) {
        Member me = PLAZA.get(nickname);
        if (me == null) {
            return;
        }
        String[] t = body.trim().split(" ");
        try {
            me.x = Plaza.clampX(Integer.parseInt(t[0]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return;
        }
        if (t.length > 1 && (t[1].equals("L") || t[1].equals("R"))) {
            me.dir = t[1];
        }
        sendToOthers(nickname, Protocol.MOVE + " " + nickname + " " + me.x + " " + me.dir);
    }

    /** 연결이 끊겼을 때 */
    public static synchronized void onLogout(String nickname) {
        if (PLAZA.remove(nickname) != null) {
            sendToOthers(nickname, Protocol.PLAZA_OUT + " " + nickname);
        }
    }

    /** 광장에 있는 동안 포프가 바뀌면(성장 등) 다른 사람 화면도 바꿔준다 */
    public static synchronized void onPopChanged(String nickname) {
        Member me = PLAZA.get(nickname);
        if (me != null) {
            sendToOthers(nickname, inLine(nickname, me));
        }
    }

    // ------------------------------------------------------------------

    /** /PLAZA_IN 닉네임 x 방향 (포프 정보: 단계 경험치 배고픔 청결 행복 유전자) */
    private static String inLine(String nickname, Member m) {
        Pop pop = PopService.get(nickname);
        String popText = pop == null ? "EGG 0 50 50 50 -" : pop.toProtocol();
        return Protocol.PLAZA_IN + " " + nickname + " " + m.x + " " + m.dir + " " + popText;
    }

    private static void sendToOthers(String nickname, String message) {
        for (Map.Entry<String, Member> e : PLAZA.entrySet()) {
            if (!e.getKey().equals(nickname)) {
                e.getValue().client.sendMessage(message);
            }
        }
    }
}
