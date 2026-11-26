package client;

import common.Emojis;
import common.Pop;
import common.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메인 창. 제안서 화면 구성대로 세 칸으로 나눈다.
 *   왼쪽  : 친구 목록 (FriendListPanel)
 *   가운데 : 게임 화면. 집(HomePanel + 돌보기 CarePanel) 과 광장(PlazaPanel) 을 CardLayout 으로 바꿔 끼움
 *   오른쪽 : 전체 채팅(ChatPanel) 과 1:1 채팅방(DmRoomPanel) 을 CardLayout 으로 바꿔 끼움
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final ServerConnection conn;
    private final String nickname;

    private final FriendListPanel friendPanel = new FriendListPanel();
    private final HomePanel homePanel;
    private final CarePanel carePanel = new CarePanel();
    private final PlazaPanel plazaPanel;

    private final CardLayout cards = new CardLayout();
    private final JPanel gameArea = new JPanel(cards);
    private final JButton moveButton = new JButton("광장으로 가기");
    private final JLabel placeLabel = new JLabel("집");

    private Pop myPop;              // 내 포프 (광장에 들고 가서 그림)
    private boolean inPlaza = false;
    private final ChatPanel chatPanel = new ChatPanel();

    // 오른쪽: 전체 채팅 <-> 1:1 채팅방
    private final CardLayout chatCards = new CardLayout();
    private final JPanel chatArea = new JPanel(chatCards);
    private final Map<String, DmRoomPanel> dmRooms = new HashMap<>();

    public MainFrame(ServerConnection conn, String nickname) {
        super("키워봐요 몽글몽글 포프 - " + nickname);
        this.conn = conn;
        this.nickname = nickname;

        homePanel = new HomePanel(nickname);
        plazaPanel = new PlazaPanel(nickname);

        setLayout(new BorderLayout());
        add(friendPanel, BorderLayout.WEST);
        // 집 = 집 화면 + 돌보기, 광장 = 광장 화면
        JPanel home = new JPanel(new BorderLayout());
        home.add(homePanel, BorderLayout.CENTER);
        home.add(carePanel, BorderLayout.SOUTH);
        gameArea.add(home, "home");
        gameArea.add(plazaPanel, "plaza");

        // 위쪽: 지금 위치 + 집/광장 이동 버튼 (채팅에 /광장, /집 을 쳐도 됨)
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        topBar.setBackground(new Color(0xFFE6EE));
        placeLabel.setFont(placeLabel.getFont().deriveFont(Font.BOLD));
        topBar.add(placeLabel, BorderLayout.WEST);
        topBar.add(moveButton, BorderLayout.EAST);
        moveButton.setFocusable(false);
        moveButton.addActionListener(e -> {
            if (inPlaza) {
                conn.send(Protocol.HOME);
            } else {
                conn.send(Protocol.PLAZA);
            }
        });

        JPanel center = new JPanel(new BorderLayout());
        center.add(topBar, BorderLayout.NORTH);
        center.add(gameArea, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // 광장에서 내가 움직이면 -> /MOVE x 방향
        plazaPanel.setMoveSender((x, dir) -> conn.send(Protocol.MOVE + " " + x + " " + dir));

        // 광장에서 다른 포프를 클릭하면 -> [친구 신청] 메뉴
        plazaPanel.setAvatarClickListener((nick, x, y) -> showAvatarMenu(nick, plazaPanel, x, y));

        // 돌보기 버튼 -> /CARE FEED|WASH|PET
        carePanel.setCareSender(action -> conn.send(Protocol.CARE + " " + action));

        // 오른쪽: 전체 채팅 (1:1 방은 열릴 때 chatArea 에 추가됨)
        chatArea.add(chatPanel, "all");
        add(chatArea, BorderLayout.EAST);

        // 채팅 패널에서 [전송]을 누르면 여기로 온다
        chatPanel.setMessageSender(this::onUserInput);

        // 친구 목록: [친구 추가] -> /FRIEND_REQ, [대화하기] -> 1:1 방 열기
        friendPanel.setAddHandler(nick -> conn.send(Protocol.FRIEND_REQ + " " + nick));
        friendPanel.setChatHandler(this::openDm);

        // 서버 연결이 끊기면 알려준다
        conn.setOnDisconnect(() -> chatPanel.appendMessage("[시스템] 서버와 연결이 끊어졌습니다."));

        // 창을 닫으면 /BYE 를 보내고 종료
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });

        chatPanel.appendMessage("[시스템] 로그인 성공! " + nickname + "님의 집입니다.");

        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(null);

        // 친구 목록 + 못 받은 친구 신청을 서버에 요청 (화면이 준비된 뒤라서 놓치지 않는다)
        conn.send(Protocol.FRIEND_LIST);
    }

    /** 사용자가 채팅창에 입력한 내용 */
    private void onUserInput(String text) {
        if (text.equalsIgnoreCase("/bye") || text.equalsIgnoreCase(Protocol.BYE)) {
            quit();
            return;
        }
        if (text.equals("/광장")) {
            conn.send(Protocol.PLAZA);
            return;
        }
        if (text.equals("/집")) {
            conn.send(Protocol.HOME);
            return;
        }
        String input = text.trim();
        if (input.equalsIgnoreCase("/PLAZA")) {
            conn.send(Protocol.PLAZA);
        } else if (input.equalsIgnoreCase("/HOME")) {
            conn.send(Protocol.HOME);
        } else if (input.startsWith("/귓속말 ") || input.equals("/귓속말")
                || input.startsWith(Protocol.WHISPER + " ") || input.equals(Protocol.WHISPER)
                || input.startsWith("/WHISPER_HISTORY ")) {
            conn.send(input);
        } else if (input.toUpperCase().startsWith(Protocol.EMOJI + " ")) {
            conn.send(Protocol.EMOJI + " " + input.substring(Protocol.EMOJI.length()).trim());
        } else if (input.equals("/이모티콘")) {
            chatPanel.appendMessage("[이모티콘] /EMOJI " + String.join(" | ", Emojis.codes()));
        } else if (!input.isEmpty()) {
            conn.send(Protocol.CHAT + " " + text);
        }
    }

    /** 서버에서 온 메시지 처리 (수신 스레드에서 호출됨) */
    public void handleServerMessage(String line) {
        if (Protocol.is(line, Protocol.ENTER)) {
            chatPanel.appendMessage("[입장] " + Protocol.body(line, Protocol.ENTER) + "님이 들어왔습니다.");
        } else if (Protocol.is(line, Protocol.LEAVE)) {
            chatPanel.appendMessage("[퇴장] " + Protocol.body(line, Protocol.LEAVE) + "님이 나갔습니다.");
        } else if (Protocol.is(line, Protocol.POP)) {
            // 내 포프 상태: /POP 단계 경험치 배고픔 청결 행복 유전자
            Pop pop = Pop.fromProtocol(Protocol.body(line, Protocol.POP));
            if (pop != null) {
                SwingUtilities.invokeLater(() -> {
                    myPop = pop;
                    homePanel.setPop(pop);
                    carePanel.setPop(pop);
                    plazaPanel.updateMyPop(pop);
                });
            }
        } else if (Protocol.is(line, Protocol.GROW)) {
            // 부화·성장: /GROW BABY|ADULT  (바로 뒤에 /POP 이 온다)
            String stage = Protocol.body(line, Protocol.GROW);
            SwingUtilities.invokeLater(homePanel::playGrow);
            if (stage.equals(Pop.Stage.BABY.name())) {
                chatPanel.appendMessage("[성장] 알이 부화했어요! 아기 포프가 태어났어요.");
            } else if (stage.equals(Pop.Stage.ADULT.name())) {
                chatPanel.appendMessage("[성장] 다 자랐어요! 최종 외형이 공개됐어요.");
            }
        } else if (Protocol.is(line, Protocol.PLAZA_OK)) {
            // 광장 입장: /PLAZA_OK 내x
            int x = parseInt(Protocol.body(line, Protocol.PLAZA_OK), common.Plaza.START_X);
            SwingUtilities.invokeLater(() -> {
                inPlaza = true;
                plazaPanel.enter(x, myPop);
                cards.show(gameArea, "plaza");
                placeLabel.setText("광장");
                moveButton.setText("집으로 가기");
                plazaPanel.requestFocusInWindow();
            });
            chatPanel.appendMessage("[이동] 광장에 왔어요.");
        } else if (Protocol.is(line, Protocol.HOME_OK)) {
            SwingUtilities.invokeLater(() -> {
                inPlaza = false;
                plazaPanel.leave();
                cards.show(gameArea, "home");
                placeLabel.setText("집");
                moveButton.setText("광장으로 가기");
            });
            chatPanel.appendMessage("[이동] 집으로 돌아왔어요.");
        } else if (Protocol.is(line, Protocol.PLAZA_IN)) {
            // /PLAZA_IN 닉네임 x 방향 단계 경험치 배고픔 청결 행복 유전자
            String[] t = Protocol.body(line, Protocol.PLAZA_IN).split(" ", 4);
            if (t.length == 4) {
                Pop pop = Pop.fromProtocol(t[3]);
                int x = parseInt(t[1], common.Plaza.START_X);
                SwingUtilities.invokeLater(() -> plazaPanel.putOther(t[0], x, t[2], pop));
            }
        } else if (Protocol.is(line, Protocol.PLAZA_OUT)) {
            String who = Protocol.body(line, Protocol.PLAZA_OUT);
            SwingUtilities.invokeLater(() -> plazaPanel.removeOther(who));
        } else if (Protocol.is(line, Protocol.MOVE)) {
            // /MOVE 닉네임 x 방향
            String[] t = Protocol.body(line, Protocol.MOVE).split(" ");
            if (t.length >= 2) {
                int x = parseInt(t[1], common.Plaza.START_X);
                String dir = t.length > 2 ? t[2] : "R";
                SwingUtilities.invokeLater(() -> plazaPanel.moveOther(t[0], x, dir));
            }
        } else if (Protocol.is(line, Protocol.FRIEND_REQ)) {
            // 친구 신청이 왔다: /FRIEND_REQ 보낸이닉
            String from = Protocol.body(line, Protocol.FRIEND_REQ);
            SwingUtilities.invokeLater(() -> {
                int r = JOptionPane.showConfirmDialog(this,
                        from + "님이 친구 신청을 보냈어요. 수락할까요?", "친구 신청", JOptionPane.YES_NO_OPTION);
                String cmd = (r == JOptionPane.YES_OPTION) ? Protocol.FRIEND_OK : Protocol.FRIEND_NO;
                conn.send(cmd + " " + from);
            });
        } else if (Protocol.is(line, Protocol.FRIEND_OK)) {
            chatPanel.appendMessage("[친구] " + Protocol.body(line, Protocol.FRIEND_OK) + "님과 친구가 됐어요!");
        } else if (Protocol.is(line, Protocol.FRIEND_NO)) {
            chatPanel.appendMessage("[친구] " + Protocol.body(line, Protocol.FRIEND_NO) + "님이 신청을 거절했어요.");
        } else if (Protocol.is(line, Protocol.FRIEND_FAIL)) {
            chatPanel.appendMessage("[친구] 신청 실패: " + Protocol.body(line, Protocol.FRIEND_FAIL));
        } else if (Protocol.is(line, Protocol.FRIEND_LIST)) {
            // /FRIEND_LIST 닉1:1 닉2:0 ...
            Map<String, Boolean> friends = new LinkedHashMap<>();
            String body = Protocol.body(line, Protocol.FRIEND_LIST);
            if (!body.isEmpty()) {
                for (String tok : body.split(" ")) {
                    int c = tok.lastIndexOf(':');
                    if (c > 0) {
                        friends.put(tok.substring(0, c), tok.substring(c + 1).equals("1"));
                    }
                }
            }
            SwingUtilities.invokeLater(() -> friendPanel.setFriends(friends));
        } else if (Protocol.is(line, Protocol.DM)) {
            // /DM 보낸이닉 내용
            String[] t = Protocol.body(line, Protocol.DM).split(" ", 2);
            if (t.length == 2) {
                SwingUtilities.invokeLater(() -> getRoom(t[0]).appendMessage(t[0] + ": " + t[1]));
                chatPanel.appendMessage("[1:1] " + t[0] + "님에게서 메시지가 왔어요.");
            }
        } else if (Protocol.is(line, Protocol.DM_FAIL)) {
            String peer = Protocol.body(line, Protocol.DM_FAIL);
            SwingUtilities.invokeLater(() ->
                    getRoom(peer).appendMessage("[시스템] 보내지 못했어요. (오프라인이거나 친구가 아님)"));
        } else if (Protocol.is(line, Protocol.EMOJI)) {
            // /EMOJI 닉네임 코드
            String[] t = Protocol.body(line, Protocol.EMOJI).split(" ", 2);
            String symbol = t.length == 2 ? Emojis.get(t[1]) : null;
            if (symbol != null) {
                chatPanel.appendMessage(t[0] + ": " + symbol);
                if (inPlaza) {
                    SwingUtilities.invokeLater(() -> plazaPanel.showBubble(t[0], symbol));
                }
            }
        } else if (line.startsWith("/WHISPER_FROM ")) {
            // 서버 형식: /WHISPER_FROM 보낸이닉 내용
            handleWhisper(true, line.substring("/WHISPER_FROM ".length()));
        } else if (line.startsWith("/WHISPER_TO ")) {
            // 서버 형식: /WHISPER_TO 받는이닉 내용
            handleWhisper(false, line.substring("/WHISPER_TO ".length()));
        } else if (Protocol.is(line, Protocol.CHAT)) {
            // 서버 형식: /CHAT 닉네임: 내용
            String body = Protocol.body(line, Protocol.CHAT);
            chatPanel.appendMessage(body);
            int colon = body.indexOf(": ");
            if (colon > 0 && inPlaza) {
                String who = body.substring(0, colon);
                String content = body.substring(colon + 2);
                SwingUtilities.invokeLater(() -> plazaPanel.showBubble(who, content));
            }
        } else {
            chatPanel.appendMessage(line);
        }
    }

    // ---------------- 귓속말 (전체 채팅 + 그 사람과의 1:1 기록 둘 다) ----------------

    /**
     * 귓속말을 전체 채팅에 [귓속말]로 보여주고, 그 사람과의 1:1 방 기록에도 남긴다.
     * body = "상대닉 내용" (받은 귓속말이면 보낸 사람, 보낸 귓속말이면 받는 사람)
     */
    private void handleWhisper(boolean received, String body) {
        String[] t = body.split(" ", 2);
        if (t.length < 2) {
            chatPanel.appendMessage("[귓속말] " + body);
            return;
        }
        String peer = t[0];
        String content = t[1];
        if (received) {
            chatPanel.appendMessage("[귓속말] " + peer + ": " + content);
            SwingUtilities.invokeLater(() -> getRoom(peer).appendMessage("[귓속말] " + peer + ": " + content));
        } else {
            chatPanel.appendMessage("[보낸 귓속말] " + peer + "에게: " + content);
            SwingUtilities.invokeLater(() -> getRoom(peer).appendMessage("[보낸 귓속말] 나: " + content));
        }
    }

    // ---------------- 1:1 채팅방 ----------------

    /** peer 와의 1:1 방을 돌려준다. 없으면 만든다. (EDT 에서만 호출) */
    private DmRoomPanel getRoom(String peer) {
        DmRoomPanel room = dmRooms.get(peer);
        if (room == null) {
            room = new DmRoomPanel(peer,
                    text -> conn.send(Protocol.DM + " " + peer + " " + text),
                    this::closeDm);
            dmRooms.put(peer, room);
            chatArea.add(room, "dm:" + peer);
        }
        return room;
    }

    /** [대화하기] -> 1:1 방으로 전환 */
    private void openDm(String peer) {
        getRoom(peer);
        chatCards.show(chatArea, "dm:" + peer);
    }

    /** 1:1 방의 X -> 전체 채팅으로 복귀. 방 기록은 그대로 남는다. */
    private void closeDm() {
        chatCards.show(chatArea, "all");
    }

    // ---------------- 광장 포프 클릭 메뉴 ----------------

    /** 광장에서 다른 포프를 클릭했을 때 호출된다. */
    public void showAvatarMenu(String nick, Component invoker, int x, int y) {
        if (nick == null || nick.equals(nickname)) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem req = new JMenuItem("친구 신청");
        req.addActionListener(e -> conn.send(Protocol.FRIEND_REQ + " " + nick));
        menu.add(req);
        menu.show(invoker, x, y);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 광장 화면 (정헌이 말풍선을 붙일 때: getPlazaPanel().showBubble(닉네임, 내용)) */
    public PlazaPanel getPlazaPanel() {
        return plazaPanel;
    }

    public boolean isInPlaza() {
        return inPlaza;
    }

    private void quit() {
        if (!conn.isClosed()) {
            conn.send(Protocol.BYE);
            conn.close();
        }
        dispose();
        System.exit(0);
    }

    public String getNickname() {
        return nickname;
    }
}