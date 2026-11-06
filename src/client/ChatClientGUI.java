
package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClientGUI {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private ChatPanel chatPanel;
    private JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI().start();
        });
    }

    public void start() {

        String nickname = JOptionPane.showInputDialog(
                null, "닉네임을 입력하세요.",
                "몽글몽글 포프 로그인",
                JOptionPane.QUESTION_MESSAGE);

        if (nickname == null || nickname.isBlank()) {
            return;
        }

        nickname = nickname.trim();

        frame = new JFrame("몽글몽글 포프 - " + nickname);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (out != null) {
                    out.println("/BYE");
                }
                closeConnection();
            }
        });

        frame.setSize(400, 550);
        frame.setLocationRelativeTo(null);

        chatPanel = new ChatPanel();
        frame.add(chatPanel, BorderLayout.CENTER);

        frame.setVisible(true);

        // 네트워크 연결은 별도 스레드에서 실행
        final String loginName = nickname;

        new Thread(() -> connect(loginName)).start();
    }

    private void connect(String nickname) {

        try {
            socket = new Socket("localhost", 9999);

            in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));

            out = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8), true);

            chatPanel.appendMessage("[시스템] 서버에 연결되었습니다.");

            // 로그인 요청
            out.println("/LOGIN " + nickname);

            // 전송 기능 연결

            chatPanel.setMessageSender(message -> {

                if (message.equalsIgnoreCase("/BYE")) {
                    out.println("/BYE");
                    chatPanel.appendMessage("[시스템] 연결을 종료합니다.");

                    SwingUtilities.invokeLater(frame::dispose);
                } else {
                    out.println("/CHAT " + message);
                }
            });


            String message;

            while ((message = in.readLine()) != null) {

                if (message.equals("/LOGIN_OK")) {
                    chatPanel.appendMessage(
                            "[시스템] 로그인 성공!");

                } else if (message.startsWith("/LOGIN_FAIL")) {
                    chatPanel.appendMessage(
                            "[시스템] " + message.substring(11));

                    closeConnection();
                    SwingUtilities.invokeLater(frame::dispose);
                    break;

                } else if (message.startsWith("/ENTER ")) {
                    chatPanel.appendMessage(
                            "[입장] " + message.substring(7));

                } else if (message.startsWith("/LEAVE ")) {
                    chatPanel.appendMessage(
                            "[퇴장] " + message.substring(7));

                } else if (message.startsWith("/CHAT ")) {
                    chatPanel.appendMessage(
                            message.substring(6));

                } else {
                    chatPanel.appendMessage(message);
                }
            }

        } catch (IOException e) {
            chatPanel.appendMessage(
                    "[시스템] 서버 연결 오류: " + e.getMessage());

        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
