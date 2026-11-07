package client;

import javax.swing.*;

/**
 * 키몽포 클라이언트 시작점. 로그인 창을 띄운다.
 *
 * 실행: java -cp out client.PopfClient
 * (서버를 먼저 켜야 함: java -cp out server.ChatServer)
 */
public class PopfClient {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
