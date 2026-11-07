
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
