package server;

import common.Protocol;

/** 친구 신청/수락/거절/목록과 1:1 메시지(/DM)를 처리한다. */
public class FriendService {

    /** 서버의 접속자 관리와 이어주는 부분. ClientHandler 에서 구현해서 넘긴다. */
    public interface Gateway {
        boolean isOnline(String nick);

        /** 접속 중인 nick 한 명에게 한 줄 보내기 */
        void send(String nick, String line);
    }

    private final FriendStore store;
    private final Gateway gw;

    public FriendService(FriendStore store, Gateway gw) {
        this.store = store;
        this.gw = gw;
    }

    /** 로그인 성공 직후 호출 */
    public void onLogin(String me) {
        store.registerUser(me);
        for (String f : store.friendsOf(me)) {
            sendList(f); // 친구들 화면에서 내가 접속중으로 바뀜
        }
    }

    /** 퇴장 처리 후 호출. 이미 접속자에서 빠진 뒤에 부를 것 */
    public void onLogout(String me) {
        for (String f : store.friendsOf(me)) {
            sendList(f);
        }
    }

    /** 이 서비스의 명령이면 처리하고 true. 아니면 false. */
    public boolean handle(String me, String line) {
        if (Protocol.is(line, Protocol.FRIEND_REQ)) {
            request(me, Protocol.body(line, Protocol.FRIEND_REQ));
        } else if (Protocol.is(line, Protocol.FRIEND_OK)) {
            accept(me, Protocol.body(line, Protocol.FRIEND_OK));
        } else if (Protocol.is(line, Protocol.FRIEND_NO)) {
            reject(me, Protocol.body(line, Protocol.FRIEND_NO));
        } else if (Protocol.is(line, Protocol.FRIEND_LIST)) {
            sendList(me);
            for (String from : store.pendingFor(me)) {
                gw.send(me, Protocol.FRIEND_REQ + " " + from); // 못 받은 신청 전달
            }
        } else if (Protocol.is(line, Protocol.DM)) {
            dm(me, Protocol.body(line, Protocol.DM));
        } else {
            return false;
        }
        return true;
    }

    private void request(String me, String target) {
        if (target.isEmpty()) {
            fail(me, "닉네임을 입력하세요.");
        } else if (target.equals(me)) {
            fail(me, "본인에게는 신청할 수 없어요.");
        } else if (!store.isKnown(target)) {
            fail(me, "없는 닉네임이에요.");
        } else if (store.areFriends(me, target)) {
            fail(me, "이미 친구예요.");
        } else if (store.hasPending(me, target)) {
            fail(me, "이미 신청을 보냈어요.");
        } else if (store.hasPending(target, me)) {
            makeFriends(target, me); // 서로 신청했으면 바로 친구
        } else {
            store.addPending(me, target);
            if (gw.isOnline(target)) {
                gw.send(target, Protocol.FRIEND_REQ + " " + me);
            }
        }
    }

    private void accept(String me, String from) {
        if (!store.removePending(from, me)) {
            fail(me, "받은 신청이 없어요.");
            return;
        }
        makeFriends(from, me);
    }

    private void makeFriends(String a, String b) {
        store.removePending(a, b);
        store.removePending(b, a);
        store.addFriends(a, b);
        if (gw.isOnline(a)) {
            gw.send(a, Protocol.FRIEND_OK + " " + b);
        }
        if (gw.isOnline(b)) {
            gw.send(b, Protocol.FRIEND_OK + " " + a);
        }
        sendList(a);
        sendList(b);
    }

    private void reject(String me, String from) {
        if (store.removePending(from, me) && gw.isOnline(from)) {
            gw.send(from, Protocol.FRIEND_NO + " " + me);
        }
    }

    private void dm(String me, String body) {
        String[] t = body.split(" ", 2);
        if (t.length < 2 || t[1].trim().isEmpty()) {
            return;
        }
        String peer = t[0];
        if (!store.areFriends(me, peer) || !gw.isOnline(peer)) {
            gw.send(me, Protocol.DM_FAIL + " " + peer);
            return;
        }
        gw.send(peer, Protocol.DM + " " + me + " " + t[1].trim());
    }

    private void sendList(String nick) {
        if (!gw.isOnline(nick)) {
            return;
        }
        StringBuilder sb = new StringBuilder(Protocol.FRIEND_LIST);
        for (String f : store.friendsOf(nick)) {
            sb.append(' ').append(f).append(':').append(gw.isOnline(f) ? 1 : 0);
        }
        gw.send(nick, sb.toString());
    }

    private void fail(String me, String reason) {
        gw.send(me, Protocol.FRIEND_FAIL + " " + reason);
    }
}