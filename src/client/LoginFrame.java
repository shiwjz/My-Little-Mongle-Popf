package client;

import common.Protocol;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * 로그인 창. 서버 주소와 닉네임을 입력하고 [접속]을 누르면
 *   1) 서버에 연결 -> 2) "/LOGIN 닉네임" 전송
 *   3) "/LOGIN_OK" 가 오면 메인 창으로 넘어가고, "/LOGIN_FAIL 이유" 가 오면 이유를 보여주고 다시 입력받는다.
 */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField hostField = new JTextField(Protocol.DEFAULT_HOST, 14);
    private final JTextField nicknameField = new JTextField(14);
    private final JButton connectButton = new JButton("접속");
    private final JLabel statusLabel = new JLabel(" ");

    public LoginFrame() {
        super("키워봐요 몽글몽글 포프 - 로그인");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        JLabel title = new JLabel("키워봐요 몽글몽글 포프", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(new Color(0xFF6B9D));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("서버 주소"), c);
        c.gridx = 1;
        form.add(hostField, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("닉네임"), c);
        c.gridx = 1;
        form.add(nicknameField, c);

        statusLabel.setForeground(new Color(0xCC4477));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(connectButton, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 28, 16, 28));
        root.setBackground(new Color(0xFFF5F8));
        form.setOpaque(false);
        bottom.setOpaque(false);
        root.add(title, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        connectButton.addActionListener(e -> tryLogin());
        nicknameField.addActionListener(e -> tryLogin()); // 엔터로도 접속

        pack();
        setLocationRelativeTo(null);
    }

    private void tryLogin() {
        String host = hostField.getText().trim();
        String nickname = nicknameField.getText().trim();

        if (nickname.isEmpty() || nickname.contains(" ")) {
            statusLabel.setText("띄어쓰기 없이 닉네임을 입력하세요.");
            return;
        }
        if (host.isEmpty()) {
            host = Protocol.DEFAULT_HOST;
        }

        setInputEnabled(false);
        statusLabel.setText("접속 중...");

        final String h = host;
        // 연결은 시간이 걸릴 수 있어서 화면(EDT)이 멈추지 않게 별도 스레드에서
        new Thread(() -> connect(h, nickname), "login").start();
    }

    private void connect(String host, String nickname) {
        ServerConnection conn;
        try {
            conn = new ServerConnection(host, Protocol.PORT);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("서버에 연결할 수 없어요: " + e.getMessage());
                setInputEnabled(true);
            });
            return;
        }

        conn.setListener(line -> {
            if (line.equals(Protocol.LOGIN_OK)) {
                openMain(conn, nickname);
            } else if (Protocol.is(line, Protocol.LOGIN_FAIL)) {
                String reason = Protocol.body(line, Protocol.LOGIN_FAIL);
                conn.setListener(l -> { });
                conn.close();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(reason.isEmpty() ? "로그인 실패" : reason);
                    setInputEnabled(true);
                });
            }
        });
        conn.send(Protocol.LOGIN + " " + nickname);
    }

    /**
     * 수신 스레드에서 호출된다. 메인 창을 다 만들 때까지(invokeAndWait) 기다렸다가
     * listener 를 메인 창으로 바꾸기 때문에, 바로 뒤에 오는 /ENTER 같은 메시지를 놓치지 않는다.
     */
    private void openMain(ServerConnection conn, String nickname) {
        final MainFrame[] holder = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new MainFrame(conn, nickname);
                holder[0].setVisible(true);
                dispose();
            });
        } catch (Exception e) {
            e.printStackTrace();
            conn.close();
            return;
        }
        conn.setListener(holder[0]::handleServerMessage);
    }

    private void setInputEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        nicknameField.setEnabled(enabled);
        connectButton.setEnabled(enabled);
    }
}
