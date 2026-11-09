package client;

import common.Pop;
import common.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 메인 창. 제안서 화면 구성대로 세 칸으로 나눈다.
 *   왼쪽  : 친구 목록 (FriendListPanel, 4주차에 채울 자리)
 *   가운데 : 게임 화면. 집(HomePanel + 돌보기 CarePanel) 과 광장(PlazaPanel) 을 CardLayout 으로 바꿔 끼움
 *   오른쪽 : 채팅 패널 (정헌이 만든 ChatPanel 그대로 사용)
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

        // 돌보기 버튼 -> /CARE FEED|WASH|PET
        carePanel.setCareSender(action -> conn.send(Protocol.CARE + " " + action));
        add(chatPanel, BorderLayout.EAST);

        // 채팅 패널에서 [전송]을 누르면 여기로 온다
        chatPanel.setMessageSender(this::onUserInput);

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
        // TODO(정헌): /귓속말 닉네임 내용 처리
        conn.send(Protocol.CHAT + " " + text);
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
        } else if (Protocol.is(line, Protocol.CHAT)) {
            // 서버 형식: /CHAT 닉네임: 내용
            chatPanel.appendMessage(Protocol.body(line, Protocol.CHAT));
        } else {
            chatPanel.appendMessage(line);
        }
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
