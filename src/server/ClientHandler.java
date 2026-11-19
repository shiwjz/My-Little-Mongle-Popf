
package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private PrintWriter out;
    private String nickname;

    // 최초 로그인 시 집에서 시작
    private volatile String location = "HOME";

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public boolean isInPlaza() {
        return "PLAZA".equals(location);
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8), true)
        ) {
            out = writer;

            String message;

            while ((message = in.readLine()) != null) {

                // 1. 로그인 처리
                if (message.startsWith("/LOGIN ")) {
                    if (nickname != null) {
                        sendMessage("/LOGIN_FAIL 이미 로그인했습니다.");
                        continue;
                    }

                    String newNickname = message.substring(7).trim();

                    if (newNickname.isEmpty()
                            || newNickname.contains(" ")) {
                        sendMessage("/LOGIN_FAIL 올바른 닉네임을 입력하세요.");
                        continue;
                    }

                    if (ChatServer.addClient(newNickname, this)) {
                        nickname = newNickname;
                        location = "HOME";

                        sendMessage("/LOGIN_OK");
                        sendMessage("/LOCATION HOME");
                    } else {
                        sendMessage("/LOGIN_FAIL 중복된 닉네임입니다.");
                    }
                    continue;
                }

                // 로그인하지 않은 사용자 차단
                if (nickname == null) {
                    sendMessage("먼저 /LOGIN 닉네임을 입력하세요.");
                    continue;
                }

                // 2. 연결 종료
                if (message.equalsIgnoreCase("/BYE")) {
                    break;
                }

                // 3. 광장 이동
                if (message.equalsIgnoreCase("/PLAZA")) {

                    if (!isInPlaza()) {
                        location = "PLAZA";

                        sendMessage("/LOCATION PLAZA");

                        // 광장 사용자에게만 입장 알림
                        ChatServer.broadcastToPlaza(
                                "/ENTER " + nickname);
                    } else {
                        sendMessage("/LOCATION PLAZA");
                    }
                    continue;
                }

                // 4. 집으로 이동
                if (message.equalsIgnoreCase("/HOME")) {

                    if (isInPlaza()) {
                        // 이동 전 광장 사람들에게 퇴장 알림
                        ChatServer.broadcastToPlaza(
                                "/LEAVE " + nickname);

                        location = "HOME";
                    }

                    sendMessage("/LOCATION HOME");
                    continue;
                }

                // 5. 귓속말
                if (message.startsWith("/귓속말 ")) {

                    String[] parts = message.split("\\s+", 3);

                    if (parts.length < 3
                            || parts[2].isBlank()) {
                        sendMessage(
                                "[시스템] 사용법: /귓속말 닉네임 내용");
                        continue;
                    }

                    String targetName = parts[1];
                    String content = parts[2];

                    ClientHandler target =
                            ChatServer.findClient(targetName);

                    if (target == null) {
                        sendMessage(
                                "[시스템] 존재하지 않는 닉네임입니다: "
                                        + targetName);
                        continue;
                    }

                    // 받는 사람에게 전달
                    target.sendMessage(
                            "/WHISPER_FROM " + nickname
                                    + " " + content);

                    // 보낸 사람에게도 표시
                    sendMessage(
                            "/WHISPER_TO " + targetName
                                    + " " + content);
                    continue;
                }

                // 6. 일반 채팅
                if (message.startsWith("/CHAT ")) {

                    String content = message.substring(6);

                    if (content.isBlank()) {
                        continue;
                    }

                    if (isInPlaza()) {
                        // 광장 사람들에게만 채팅 전달
                        ChatServer.broadcastToPlaza(
                                "/CHAT " + nickname
                                        + ": " + content);
                    } else {
                        // 집에서는 자신에게만 표시
                        sendMessage(
                                "/CHAT " + nickname
                                        + ": " + content);
                    }
                    continue;
                }

                sendMessage("[시스템] 알 수 없는 명령어입니다.");
            }

        } catch (IOException e) {
            System.out.println(
                    "클라이언트 연결 오류: " + e.getMessage());

        } finally {

            if (nickname != null) {

                // 광장에 있었던 경우에만 퇴장 알림
                if (isInPlaza()) {
                    ChatServer.broadcastToPlaza(
                            "/LEAVE " + nickname);
                }

                ChatServer.removeClient(nickname, this);
            }

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("클라이언트 연결 종료");
        }
    }
}
