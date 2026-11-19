package server;

import common.Gene;
import common.Plaza;
import common.Pop;
import common.Protocol;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * 교배 (예민 담당).
 *
 * 사용자끼리:
 *   요청자 /BREED_REQ 상대 -> 서버가 조건 확인 -> 요청자에게 /BREED_SENT, 상대에게 /BREED_ASK 요청자
 *   상대  /BREED_ACCEPT 요청자 -> 서버가 다시 확인하고 알 2개를 계산 -> 양쪽에 /EGG 하나씩 (보관함에 들어감)
 *   상대  /BREED_REJECT 요청자 -> 요청자에게 /BREED_REJECT 상대
 * NPC 와는 수락 없이 바로 교배하고, 요청한 사람만 알 1개를 받는다.
 *
 * 조건: 둘 다 광장에 있고, 둘 다 지금 키우는 포프가 성체이고, 보관함에 자리가 있어야 한다.
 * 교배 결과(유전자)는 서버에서만 계산한다 (Gene.breed).
 */
public final class BreedService {

    private BreedService() {
    }

    /** 대기 중인 신청: 상대 닉네임 -> 요청자 닉네임 (한 사람에게는 한 번에 하나의 신청만) */
    private static final Map<String, String> PENDING = new HashMap<>();
    private static final Random RANDOM = new Random();

    public static synchronized void request(ClientHandler client, String nickname, String targetName) {
        targetName = targetName.trim();
        String problem = checkSelf(nickname);
        if (problem != null) {
            fail(client, problem);
            return;
        }
        if (targetName.isEmpty() || targetName.equals(nickname)) {
            fail(client, "교배할 상대를 골라주세요.");
            return;
        }

        // NPC 와는 바로 교배
        Plaza.Npc npc = findNpc(targetName);
        if (npc != null) {
            Gene child = Gene.breed(PopService.get(nickname).gene, Gene.parse(npc.gene), RANDOM);
            if (PopService.addEgg(nickname, child)) {
                client.sendMessage(eggLine(npc.name, child));
                System.out.println("[교배] " + nickname + " x " + npc.name + " (NPC)");
            } else {
                fail(client, "보관함이 꽉 찼어요.");
            }
            return;
        }

        ClientHandler target = ChatServer.findClient(targetName);
        if (target == null) {
            fail(client, targetName + "님은 접속해 있지 않아요.");
            return;
        }
        String targetProblem = checkOther(targetName);
        if (targetProblem != null) {
            fail(client, targetProblem);
            return;
        }
        if (PENDING.containsKey(targetName)) {
            fail(client, targetName + "님은 다른 교배 신청을 확인하고 있어요.");
            return;
        }
        if (PENDING.containsValue(nickname)) {
            fail(client, "이미 보낸 교배 신청의 대답을 기다리는 중이에요.");
            return;
        }

        PENDING.put(targetName, nickname);
        client.sendMessage(Protocol.BREED_SENT + " " + targetName);
        target.sendMessage(Protocol.BREED_ASK + " " + nickname);
        System.out.println("[교배] " + nickname + " -> " + targetName + " 신청");
    }

    /** 신청받은 사람(nickname)이 수락 */
    public static synchronized void accept(ClientHandler client, String nickname, String requesterName) {
        requesterName = requesterName.trim();
        if (!requesterName.equals(PENDING.get(nickname))) {
            fail(client, "대기 중인 교배 신청이 없어요.");
            return;
        }
        PENDING.remove(nickname);

        ClientHandler requester = ChatServer.findClient(requesterName);
        if (requester == null) {
            fail(client, requesterName + "님이 나가서 교배가 취소됐어요.");
            return;
        }
        // 수락하는 사이에 상황이 바뀌었을 수 있으니 다시 확인
        String problem = checkSelf(nickname);
        if (problem == null) {
            problem = checkOther(requesterName);
        }
        if (problem != null) {
            fail(client, problem);
            fail(requester, "교배가 취소됐어요: " + problem);
            return;
        }

        Gene a = PopService.get(requesterName).gene;
        Gene b = PopService.get(nickname).gene;
        Gene eggForRequester = Gene.breed(a, b, RANDOM);
        Gene eggForTarget = Gene.breed(a, b, RANDOM);   // 알마다 따로 주사위를 굴려서 둘이 다를 수 있음
        PopService.addEgg(requesterName, eggForRequester);
        PopService.addEgg(nickname, eggForTarget);
        requester.sendMessage(eggLine(nickname, eggForRequester));
        client.sendMessage(eggLine(requesterName, eggForTarget));
        System.out.println("[교배] " + requesterName + " x " + nickname + " 성공, 알 2개");
    }

    /** 신청받은 사람(nickname)이 거절 */
    public static synchronized void reject(ClientHandler client, String nickname, String requesterName) {
        requesterName = requesterName.trim();
        if (!requesterName.equals(PENDING.get(nickname))) {
            return;
        }
        PENDING.remove(nickname);
        ClientHandler requester = ChatServer.findClient(requesterName);
        if (requester != null) {
            requester.sendMessage(Protocol.BREED_REJECT + " " + nickname);
        }
    }

    /** 연결이 끊기면 그 사람이 관련된 신청을 모두 취소 */
    public static synchronized void onLogout(String nickname) {
        String waitingRequester = PENDING.remove(nickname);
        if (waitingRequester != null) {
            ClientHandler r = ChatServer.findClient(waitingRequester);
            if (r != null) {
                fail(r, nickname + "님이 나가서 교배 신청이 취소됐어요.");
            }
        }
        Iterator<Map.Entry<String, String>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (e.getValue().equals(nickname)) {
                it.remove();
                ClientHandler t = ChatServer.findClient(e.getKey());
                if (t != null) {
                    fail(t, nickname + "님이 나가서 교배 신청이 취소됐어요.");
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /** 내가 교배할 수 있는 상태인지. 문제가 있으면 이유, 없으면 null */
    private static String checkSelf(String nickname) {
        Pop pop = PopService.get(nickname);
        if (!PlazaService.isInPlaza(nickname)) {
            return "교배는 광장에서만 할 수 있어요.";
        }
        if (pop == null || pop.stage != Pop.Stage.ADULT) {
            return "다 자란(성체) 포프만 교배할 수 있어요.";
        }
        if (PopService.isBoxFull(nickname)) {
            return "보관함이 꽉 찼어요.";
        }
        return null;
    }

    private static String checkOther(String name) {
        Pop pop = PopService.get(name);
        if (!PlazaService.isInPlaza(name)) {
            return name + "님이 광장에 없어요.";
        }
        if (pop == null || pop.stage != Pop.Stage.ADULT) {
            return name + "님의 포프는 아직 다 자라지 않았어요.";
        }
        if (PopService.isBoxFull(name)) {
            return name + "님의 보관함이 꽉 찼어요.";
        }
        return null;
    }

    private static Plaza.Npc findNpc(String name) {
        for (Plaza.Npc npc : Plaza.NPCS) {
            if (npc.name.equals(name)) {
                return npc;
            }
        }
        return null;
    }

    /** /EGG 교배상대 돌연변이수 유전자 */
    private static String eggLine(String partner, Gene child) {
        return Protocol.EGG + " " + partner + " " + child.getMutationCount() + " " + child;
    }

    private static void fail(ClientHandler client, String reason) {
        client.sendMessage(Protocol.BREED_FAIL + " " + reason);
    }
}
