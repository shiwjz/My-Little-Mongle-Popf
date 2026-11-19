
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

    public static boolean addClient(String nickname, ClientHandler handler) {
        return clients.putIfAbsent(nickname, handler) == null;
    }

    public static void removeClient(String nickname, ClientHandler handler) {
        clients.remove(nickname, handler);
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    // 현재 접속자 검색 (귓속말용)
    public static ClientHandler findClient(String nickname) {
        return clients.get(nickname);
    }

    // 1:1 귓속말 기록: 서버 실행 중 메모리에만 보관 (영구 저장 아님)
    private static final Map<String, java.util.List<String>> whisperHistory
            = new ConcurrentHashMap<>();

    private static String conversationKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a.length() + ":" + a + b : b.length() + ":" + b + a;
    }

    public static void saveWhisper(String sender, String receiver, String content) {
        String key = conversationKey(sender, receiver);
        java.util.List<String> conversation = whisperHistory.computeIfAbsent(
                key, ignored -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        synchronized (conversation) {
            conversation.add(sender + ": " + content);
            // 무제한 메모리 증가 방지
            if (conversation.size() > 200) conversation.remove(0);
        }
    }

    public static java.util.List<String> getWhisperHistory(String a, String b) {
        java.util.List<String> conversation = whisperHistory.get(conversationKey(a, b));
        if (conversation == null) return java.util.Collections.emptyList();
        synchronized (conversation) {
            return new java.util.ArrayList<>(conversation);
        }
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
