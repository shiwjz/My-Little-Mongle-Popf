
package client;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ChatPanel extends JPanel {

    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;

    private Consumer<String> messageSender;

    public ChatPanel() {

        setLayout(new BorderLayout(5, 5));
        setPreferredSize(new Dimension(300, 500));

        // 채팅 기록 영역
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        // 메시지 입력 영역
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        inputField = new JTextField();
        sendButton = new JButton("전송");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        // 버튼 클릭 시 전송
        sendButton.addActionListener(e -> sendMessage());

        // Enter 키 입력 시 전송
        inputField.addActionListener(e -> sendMessage());
    }

    // 메시지 전송
    private void sendMessage() {

        String message = inputField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        if (messageSender != null) {
            messageSender.accept(message);
            inputField.setText("");
        }
    }

    // 네트워크 송신 기능 연결
    public void setMessageSender(Consumer<String> sender) {
        this.messageSender = sender;
    }

    // 채팅 메시지 화면 출력
    public void appendMessage(String message) {

        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(
                    chatArea.getDocument().getLength());
        });
    }
}
