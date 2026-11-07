package client;

import common.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 메인 창. 제안서 화면 구성대로 세 칸으로 나눈다.
 *   왼쪽  : 친구 목록 (FriendListPanel, 4주차에 채울 자리)
 *   가운데 : 게임 화면 (지금은 집 화면 HomePanel, 3주차에 광장 화면과 바꿔 끼움)
 *   오른쪽 : 채팅 패널 (정헌이 만든 ChatPanel 그대로 사용)
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final ServerConnection conn;
    private final String nickname;

    private final FriendListPanel friendPanel = new FriendListPanel();
    private final HomePanel homePanel;
    private final ChatPanel chatPanel = new ChatPanel();

    public MainFrame(ServerConnection conn, String nickname) {
        super("키워봐요 몽글몽글 포프 - " + nickname);
        this.conn = conn;
        this.nickname = nickname;

        homePanel = new HomePanel(nickname);

        setLayout(new BorderLayout());
        add(friendPanel, BorderLayout.WEST);
        add(homePanel, BorderLayout.CENTER);
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

        setSize(1100, 620);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(null);
    }

    /** 사용자가 채팅창에 입력한 내용 */
    private void onUserInput(String text) {
        if (text.equalsIgnoreCase("/bye") || text.equalsIgnoreCase(Protocol.BYE)) {
            quit();
            return;
        }
        // 2주차 이후 /광장, /집, /귓속말 같은 명령어 처리를 여기에 추가
        conn.send(Protocol.CHAT + " " + text);
    }

    /** 서버에서 온 메시지 처리 (수신 스레드에서 호출됨) */
    public void handleServerMessage(String line) {
        if (Protocol.is(line, Protocol.ENTER)) {
            chatPanel.appendMessage("[입장] " + Protocol.body(line, Protocol.ENTER) + "님이 들어왔습니다.");
        } else if (Protocol.is(line, Protocol.LEAVE)) {
            chatPanel.appendMessage("[퇴장] " + Protocol.body(line, Protocol.LEAVE) + "님이 나갔습니다.");
        } else if (Protocol.is(line, Protocol.CHAT)) {
            // 서버 형식: /CHAT 닉네임: 내용
            chatPanel.appendMessage(Protocol.body(line, Protocol.CHAT));
        } else {
            chatPanel.appendMessage(line);
        }
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
