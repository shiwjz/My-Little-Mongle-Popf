
package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import common.Protocol;

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
                // 클라이언트(ChatClientGUI, PopfClient)와 맞춰 UTF-8로 고정 (윈도우에서 한글 깨짐 방지)
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
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
                        // 접속 직후에는 HOME: 광장 입장 알림은 /PLAZA에서만 전송
                        // 내 포프 불러오기 (처음이면 랜덤 알 지급) -> /POP
                        PopService.onLogin(this, nickname);
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

                // 광장 이동 (예민) : /PLAZA, /HOME, /MOVE x 방향
                if (message.equals("/PLAZA")) {
                    boolean joiningPlaza = !PlazaService.isInPlaza(nickname);
                    PlazaService.enter(this, nickname);
                    if (joiningPlaza) {
                        PlazaService.broadcastToPlaza("/ENTER " + nickname);
                    }
                    continue;
                }
                if (message.equals("/HOME")) {
                    if (PlazaService.isInPlaza(nickname)) {
                        PlazaService.broadcastToPlaza("/LEAVE " + nickname);
                    }
                    PlazaService.leave(this, nickname);
                    continue;
                }
                if (message.startsWith("/MOVE ")) {
                    PlazaService.move(nickname, message.substring(6));
                    continue;
                }

                // 포프 돌보기 (예민) : /CARE FEED|WASH|PET
                if (message.startsWith("/CARE ")) {
                    PopService.onCare(this, nickname, message.substring(6).trim());
                    continue;
                }

                // 청혼 (예민) : /PROPOSE 상대, /PROPOSE_ACCEPT 신청자, /PROPOSE_REJECT 신청자
                if (message.startsWith(Protocol.PROPOSE_ACCEPT + " ")) {
                    BreedService.accept(this, nickname, message.substring(Protocol.PROPOSE_ACCEPT.length() + 1));
                    continue;
                }
                if (message.startsWith(Protocol.PROPOSE_REJECT + " ")) {
                    BreedService.reject(this, nickname, message.substring(Protocol.PROPOSE_REJECT.length() + 1));
                    continue;
                }
                if (message.startsWith(Protocol.PROPOSE + " ")) {
                    BreedService.request(this, nickname, message.substring(Protocol.PROPOSE.length() + 1));
                    continue;
                }

                                // 귓속말: /귓속말 닉네임 내용 또는 /WHISPER 닉네임 내용
                if (message.startsWith("/귓속말 ") || message.startsWith("/WHISPER ")
                        || message.equals("/귓속말") || message.equals("/WHISPER")) {
                    String command = message.startsWith("/WHISPER") ? "/WHISPER" : "/귓속말";
                    String body = message.substring(command.length()).trim();
                    String[] parts = body.split("\\s+", 2);
                    if (parts.length < 2 || parts[1].isBlank()) {
                        sendMessage("[시스템] 사용법: /귓속말 닉네임 내용");
                        continue;
                    }
                    String targetName = parts[0];
                    String content = parts[1];
                    ClientHandler target = ChatServer.findClient(targetName);
                    if (target == null) {
                        sendMessage("[시스템] 존재하지 않는 닉네임입니다: " + targetName);
                        continue;
                    }
                    ChatServer.saveWhisper(nickname, targetName, content);
                    target.sendMessage("/WHISPER_FROM " + nickname + " " + content);
                    sendMessage("/WHISPER_TO " + targetName + " " + content);
                    continue;
                }

                // 1:1 대화 내역 조회 (현재 서버 세션 내 기록)
                if (message.startsWith("/WHISPER_HISTORY ")) {
                    String targetName = message.substring("/WHISPER_HISTORY ".length()).trim();
                    if (targetName.isEmpty()) {
                        sendMessage("[시스템] 사용법: /WHISPER_HISTORY 닉네임");
                        continue;
                    }
                    sendMessage("[1:1 기록] " + targetName);
                    for (String entry : ChatServer.getWhisperHistory(nickname, targetName)) {
                        sendMessage("[1:1] " + entry);
                    }
                    continue;
                }

                // 전체 채팅
                if (message.startsWith("/CHAT ")) {
                    String content = message.substring(6);

                    if (!content.isBlank()) {
                        if (PlazaService.isInPlaza(nickname)) {
                            PlazaService.broadcastToPlaza("/CHAT " + nickname + ": " + content);
                        } else {
                            sendMessage("/CHAT " + nickname + ": " + content);
                        }
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
                if (PlazaService.isInPlaza(nickname)) {
                    PlazaService.broadcastToPlaza("/LEAVE " + nickname);
                }
                BreedService.onLogout(nickname);
                PlazaService.onLogout(nickname);
                PopService.onLogout(nickname);
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
