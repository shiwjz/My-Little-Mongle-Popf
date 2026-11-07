package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 서버와의 연결 하나를 관리한다.
 * - send(): 서버로 한 줄 보내기
 * - 수신 스레드: 서버에서 오는 줄을 계속 읽어서 지금 등록된 listener 에게 넘긴다
 *
 * 로그인 창 -> 메인 창으로 넘어갈 때 연결은 그대로 두고 listener 만 바꿔 끼운다.
 * 한글이 깨지지 않도록 입출력을 모두 UTF-8로 맞춘다 (서버도 UTF-8).
 */
public class ServerConnection {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    private volatile Consumer<String> listener = line -> { };
    private volatile Runnable onDisconnect = () -> { };

    public ServerConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        Thread receiver = new Thread(this::receiveLoop, "server-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    private void receiveLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                listener.accept(line);
            }
        } catch (IOException e) {
            // 소켓을 닫으면 여기로 온다
        } finally {
            close();
            onDisconnect.run();
        }
    }

    public void send(String line) {
        out.println(line);
    }

    /** 서버에서 줄이 올 때마다 호출될 함수 (수신 스레드에서 불린다) */
    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    /** 서버와 연결이 끊겼을 때 호출될 함수 */
    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
