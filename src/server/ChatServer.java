
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    // 현재 접속한 사용자 목록
    private static final Map<String, ClientHandler> clients
            = new ConcurrentHashMap<>();

    // 사용자 등록 (닉네임 중복 방지)
    public static boolean addClient(String nickname, ClientHandler handler) {
        return clients.putIfAbsent(nickname, handler) == null;
    }

    // 사용자 제거
    public static void removeClient(String nickname, ClientHandler handler) {
        clients.remove(nickname, handler);
    }

    // 기존 전체 전송 기능 유지
    public static void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    // 광장에 있는 사용자에게만 메시지 전송
    public static void broadcastToPlaza(String message) {
        for (ClientHandler client : clients.values()) {
            if (client.isInPlaza()) {
                client.sendMessage(message);
            }
        }
    }

    // 귓속말 대상 사용자 검색
    public static ClientHandler findClient(String nickname) {
        return clients.get(nickname);
    }

    // 특정 사용자에게 메시지 전송
    public static boolean sendWhisper(String nickname, String message) {
        ClientHandler target = findClient(nickname);

        if (target == null) {
            return false;
        }

        target.sendMessage(message);
        return true;
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(9999)) {

            System.out.println("몽글몽글 포프 서버 시작!");
            System.out.println("클라이언트 접속 대기 중...");

            while (true) {
                Socket socket = serverSocket.accept();

                System.out.println("클라이언트 접속: "
                        + socket.getInetAddress());

                ClientHandler handler = new ClientHandler(socket);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
