package client;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** 1:1 채팅방. 위에 상대 이름과 X 버튼, 아래는 ChatPanel. */
public class DmRoomPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ChatPanel chat = new ChatPanel();

    public DmRoomPanel(String peer, Consumer<String> sender, Runnable onClose) {
        super(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        header.setBackground(new Color(0xFFE6EE));
        JLabel title = new JLabel(peer + "님과 1:1 대화");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JButton close = new JButton("X");
        close.setFocusable(false);
        close.addActionListener(e -> onClose.run());
        header.add(title, BorderLayout.WEST);
        header.add(close, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(chat, BorderLayout.CENTER);

        chat.setMessageSender(text -> {
            String t = text.trim();
            if (!t.isEmpty()) {
                sender.accept(t);              // /DM 상대닉 내용
                appendMessage("나: " + t);     // 서버는 보낸 사람에게 되돌려주지 않으니 직접 표시
            }
        });
    }

    public void appendMessage(String s) {
        chat.appendMessage(s);
    }
}