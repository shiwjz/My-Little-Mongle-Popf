
package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private PrintWriter out;
    private String nickname;

    public ClientHandler(Socket socket) {
        this.socket = socket;
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
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(), true)
        ) {
            out = writer;

            String message;

            while ((message = in.readLine()) != null) {

                // 로그인 처리
                if (message.startsWith("/LOGIN ")) {
                    if (nickname != null) {
                        sendMessage("/LOGIN_FAIL 이미 로그인했습니다.");
                        continue;
                    }

                    String newNickname = message.substring(7).trim();

                    if (newNickname.isEmpty() || newNickname.contains(" ")) {
                        sendMessage("/LOGIN_FAIL 올바른 닉네임을 입력하세요.");
                        continue;
                    }

                    if (ChatServer.addClient(newNickname, this)) {
                        nickname = newNickname;
                        sendMessage("/LOGIN_OK");
                        ChatServer.broadcast("/ENTER " + nickname);
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

                // 종료 처리
                if (message.equalsIgnoreCase("/BYE")) {
                    break;
                }

                // 전체 채팅
                if (message.startsWith("/CHAT ")) {
                    String content = message.substring(6);

                    if (!content.isBlank()) {
                        ChatServer.broadcast("/CHAT " + nickname
                                + ": " + content);
                    }
                } else {
                    sendMessage("알 수 없는 명령어입니다.");
                }
            }

        } catch (IOException e) {
            System.out.println("클라이언트 연결 오류: "
                    + e.getMessage());

        } finally {
            if (nickname != null) {
                ChatServer.removeClient(nickname, this);
                ChatServer.broadcast("/LEAVE " + nickname);
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
