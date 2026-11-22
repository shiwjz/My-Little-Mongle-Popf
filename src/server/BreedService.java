package server;

import common.Gene;
import common.Plaza;
import common.Pop;
import common.Protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 청혼(교배) (예민 담당).
 *
 * 사용자끼리:
 *   신청자 /PROPOSE 상대 -> 서버가 조건 확인 -> 광장에 /PROPOSE_SAY (신청자 포프가 청혼 대사)
 *                       -> 상대에게 /PROPOSE_ASK 신청자 (상대 화면에서 2초 뒤 받아들일지 묻는 창)
 *   상대  /PROPOSE_ACCEPT 신청자 -> 다시 확인 -> 광장에 /PROPOSE_ANSWER (대답 대사)
 *                       -> 2초 뒤(대답 말풍선을 볼 시간) 둘 다 새 알을 키우고 집으로 돌아감
 *                          지금 포프는 도감으로 (/EGG, /POP, /DEX, /HOME_OK). 알 상태로 광장에 남지 않는다
 *   상대  /PROPOSE_REJECT 신청자 -> 광장에 /PROPOSE_ANSWER ... NO (거절 대사)
 * NPC 에게 청혼하면 2초 뒤 NPC 가 대답한다. 20% 확률로 거절, 받아주면 2초 뒤 신청자만 새 알을 받고 집으로 간다.
 *
 * 조건: 둘 다 광장에 있고, 둘 다 지금 키우는 포프가 성체여야 한다.
 * 유전자는 서버에서만 계산한다 (Gene.breed).
 *
 * 락 순서: BreedService -> PlazaService -> PopService 한 방향만 (다른 서비스는 BreedService 를 부르지 않음)
 */
public final class BreedService {

    private BreedService() {
    }

    /** NPC 가 청혼을 거절할 확률 */
    public static final double NPC_REJECT_CHANCE = 0.2;
    /** NPC 가 대답하기까지 (청혼 대사를 읽을 시간) */
    private static final long NPC_ANSWER_MS = 2000;
    /** 청혼 성공 후 집으로 돌아가기까지 (대답 말풍선을 읽을 시간) */
    private static final long GO_HOME_MS = 2000;

    /** 청혼 대사 (신청자 포프가 말함) */
    private static final String[] PROPOSE_LINES = {
            "우리의 별자리는 운명이야★",
            "널 만난 건 기적이야!",
            "나랑 평생 몽글몽글할래?",
            "우리 둘이 가족이 되자♡",
            "내 하트는 너로 가득해!",
            "너랑 있으면 매일이 반짝여!",
            "우리의 이야기를 이어가자!",
            "내 옆자리는 네 거야♡",
            "너랑 닮은 아기를 만나고 싶어!",
            "내 마음을 받아줄래?♥"
    };
    /** 받아줄 때 대사 */
    private static final String[] YES_LINES = {
            "기뻐! 너와 함께 행복해지고 싶어",
            "나도 좋아! 평생 함께하자♡",
            "기다리고 있었어★"
    };
    /** 거절할 때 대사 */
    private static final String[] NO_LINES = {
            "미안… 아직 마음의 준비가 안 됐어",
            "우린 좋은 친구로 지내자!",
            "조금만 더 생각해볼게…"
    };

    /** 대답을 기다리는 청혼: 상대 닉네임 -> 신청자 닉네임 (한 사람에게는 한 번에 하나) */
    private static final Map<String, String> PENDING = new HashMap<>();
    /** NPC 의 대답을 기다리는 신청자 */
    private static final Set<String> NPC_WAITING = new HashSet<>();
    /** 청혼에 성공해서 집에 돌아가기를 기다리는 사람 (그동안 또 청혼할 수 없음) */
    private static final Set<String> GOING_HOME = new HashSet<>();
    private static final Random RANDOM = new Random();
    /** NPC 대답을 2초 뒤에 처리하는 스레드 (서버가 꺼질 때 같이 꺼지도록 데몬) */
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "npc-answer");
        t.setDaemon(true);
        return t;
    });

    public static synchronized void request(ClientHandler client, String nickname, String targetName) {
        targetName = targetName.trim();
        String problem = checkSelf(nickname);
        if (problem != null) {
            fail(client, problem);
            return;
        }
        if (targetName.isEmpty() || targetName.equals(nickname)) {
            fail(client, "청혼할 상대를 골라주세요.");
            return;
        }
        if (isWaiting(nickname)) {
            fail(client, "이미 한 청혼의 대답을 기다리는 중이에요.");
            return;
        }

        Plaza.Npc npc = findNpc(targetName);
        ClientHandler target = null;
        if (npc == null) {
            target = ChatServer.findClient(targetName);
            if (target == null) {
                fail(client, targetName + "님은 접속해 있지 않아요.");
                return;
            }
            String targetProblem = checkOther(targetName);
            if (targetProblem != null) {
                fail(client, targetProblem);
                return;
            }
            if (PENDING.containsKey(targetName) || isWaiting(targetName)) {
                fail(client, targetName + "님은 지금 다른 청혼 중이에요.");
                return;
            }
        }

        // 광장 모두에게 청혼 대사 (신청자 포프 위 말풍선)
        String line = pick(PROPOSE_LINES);
        PlazaService.broadcastToPlaza(Protocol.PROPOSE_SAY + " " + nickname + " " + targetName + " " + line);
        System.out.println("[청혼] " + nickname + " -> " + targetName + ": " + line);

        if (npc != null) {
            NPC_WAITING.add(nickname);
            String npcName = npc.name;
            String npcGene = npc.gene;
            TIMER.schedule(() -> npcAnswer(nickname, npcName, npcGene), NPC_ANSWER_MS, TimeUnit.MILLISECONDS);
        } else {
            PENDING.put(targetName, nickname);
            target.sendMessage(Protocol.PROPOSE_ASK + " " + nickname);
        }
    }

    /** 2초 뒤 NPC 의 대답 (TIMER 스레드) */
    private static synchronized void npcAnswer(String nickname, String npcName, String npcGene) {
        if (!NPC_WAITING.remove(nickname)) {
            return;   // 그사이 나갔음
        }
        ClientHandler client = ChatServer.findClient(nickname);
        if (client == null) {
            return;
        }
        String problem = checkSelf(nickname);
        if (problem != null) {
            fail(client, "청혼이 취소됐어요: " + problem);
            return;
        }
        if (RANDOM.nextDouble() < NPC_REJECT_CHANCE) {
            answer(npcName, nickname, false);
            return;
        }
        answer(npcName, nickname, true);
        Gene child = Gene.breed(PopService.get(nickname).gene, Gene.parse(npcGene), RANDOM);
        goHomeLater(nickname, npcName, child);
        System.out.println("[청혼] " + nickname + " x " + npcName + " (NPC) 성공");
    }

    /** 청혼받은 사람(nickname)이 받아들임 */
    public static synchronized void accept(ClientHandler client, String nickname, String requesterName) {
        requesterName = requesterName.trim();
        if (!requesterName.equals(PENDING.get(nickname))) {
            fail(client, "대답을 기다리는 청혼이 없어요.");
            return;
        }
        PENDING.remove(nickname);

        ClientHandler requester = ChatServer.findClient(requesterName);
        if (requester == null) {
            fail(client, requesterName + "님이 나가서 청혼이 취소됐어요.");
            return;
        }
        // 고민하는 사이에 상황이 바뀌었을 수 있으니 다시 확인
        String problem = checkSelf(nickname);
        if (problem == null) {
            problem = checkOther(requesterName);
        }
        if (problem != null) {
            fail(client, problem);
            fail(requester, "청혼이 취소됐어요: " + problem);
            return;
        }

        answer(nickname, requesterName, true);
        Gene a = PopService.get(requesterName).gene;
        Gene b = PopService.get(nickname).gene;
        Gene eggForRequester = Gene.breed(a, b, RANDOM);
        Gene eggForTarget = Gene.breed(a, b, RANDOM);   // 알마다 따로 주사위를 굴려서 둘이 다를 수 있음
        goHomeLater(requesterName, nickname, eggForRequester);
        goHomeLater(nickname, requesterName, eggForTarget);
        System.out.println("[청혼] " + requesterName + " x " + nickname + " 성공, 알 2개");
    }

    /** 청혼받은 사람(nickname)이 거절 */
    public static synchronized void reject(ClientHandler client, String nickname, String requesterName) {
        requesterName = requesterName.trim();
        if (!requesterName.equals(PENDING.get(nickname))) {
            return;
        }
        PENDING.remove(nickname);
        answer(nickname, requesterName, false);
    }

    /** 연결이 끊기면 그 사람이 관련된 청혼을 모두 취소 */
    public static synchronized void onLogout(String nickname) {
        NPC_WAITING.remove(nickname);
        GOING_HOME.remove(nickname);
        String waitingRequester = PENDING.remove(nickname);
        if (waitingRequester != null) {
            ClientHandler r = ChatServer.findClient(waitingRequester);
            if (r != null) {
                fail(r, nickname + "님이 나가서 청혼이 취소됐어요.");
            }
        }
        Iterator<Map.Entry<String, String>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (e.getValue().equals(nickname)) {
                it.remove();
                ClientHandler t = ChatServer.findClient(e.getKey());
                if (t != null) {
                    fail(t, nickname + "님이 나가서 청혼이 취소됐어요.");
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /** 광장에 대답 대사: /PROPOSE_ANSWER 대답한사람 신청자 YES|NO 대사 */
    private static void answer(String who, String requester, boolean yes) {
        String line = pick(yes ? YES_LINES : NO_LINES);
        PlazaService.broadcastToPlaza(Protocol.PROPOSE_ANSWER + " " + who + " " + requester + " "
                + (yes ? "YES" : "NO") + " " + line);
    }

    /** 대답 말풍선을 볼 수 있게 2초 기다렸다가 새 알을 주고 집으로 보낸다 */
    private static void goHomeLater(String nickname, String partner, Gene child) {
        GOING_HOME.add(nickname);
        TIMER.schedule(() -> newGeneration(nickname, partner, child), GO_HOME_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 새 알을 키우게 하고(지금 포프는 도감으로) 집으로 보낸다 (TIMER 스레드).
     * 그 2초 사이에 접속이 끊긴 사람은 청혼이 없던 것으로 하고 지금 포프를 그대로 둔다.
     */
    private static synchronized void newGeneration(String nickname, String partner, Gene child) {
        GOING_HOME.remove(nickname);
        ClientHandler client = ChatServer.findClient(nickname);
        if (client == null) {
            return;
        }
        client.sendMessage(Protocol.EGG + " " + partner + " " + child.getMutationCount() + " " + child);
        PopService.startNewGeneration(nickname, child, partner);
        // 알은 광장에 있을 수 없으니 집으로 (/HOME 을 받은 것과 똑같이 처리)
        if (PlazaService.isInPlaza(nickname)) {
            PlazaService.broadcastToPlaza("/LEAVE " + nickname);
        }
        PlazaService.leave(client, nickname);
    }

    /** 신청자로서 대답을 기다리는 중이거나, 청혼에 성공해서 집에 가는 중인지 */
    private static boolean isWaiting(String nickname) {
        return PENDING.containsValue(nickname) || NPC_WAITING.contains(nickname) || GOING_HOME.contains(nickname);
    }

    /** 내가 청혼할 수 있는 상태인지. 문제가 있으면 이유, 없으면 null */
    private static String checkSelf(String nickname) {
        Pop pop = PopService.get(nickname);
        if (!PlazaService.isInPlaza(nickname)) {
            return "청혼은 광장에서만 할 수 있어요.";
        }
        if (pop == null || pop.stage != Pop.Stage.ADULT) {
            return "다 자란(성체) 포프만 청혼할 수 있어요.";
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

    private static String pick(String[] lines) {
        return lines[RANDOM.nextInt(lines.length)];
    }

    private static void fail(ClientHandler client, String reason) {
        client.sendMessage(Protocol.PROPOSE_FAIL + " " + reason);
    }
}
