
package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {

    public static void main(String[] args) {

        try (
                Socket socket = new Socket("localhost", 9999);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("몽글몽글 포프 서버에 연결되었습니다!");

            // 서버 메시지 수신 스레드
            Thread receiveThread = new Thread(() -> {
                try {
                    String message;

                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }

                    System.out.println("서버와의 연결이 종료되었습니다.");
                } catch (IOException e) {
                    System.out.println("서버 수신 오류: " + e.getMessage());
                }
            });

            receiveThread.setDaemon(true);
            receiveThread.start();

            System.out.print("닉네임 입력: ");
            String nickname = scanner.nextLine().trim();
            out.println("/LOGIN " + nickname);

            System.out.println("채팅 메시지를 입력하세요. (/BYE 종료)");

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/BYE")) {
                    out.println("/BYE");
                    break;
                }

                if (!message.isBlank()) {
                    out.println("/CHAT " + message);
                }
            }

        } catch (IOException e) {
            System.out.println("서버 연결 오류: " + e.getMessage());
        }
    }
}
