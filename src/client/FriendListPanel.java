package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 메인 창 왼쪽의 친구 목록.
 * 서버의 /FRIEND_LIST 를 받으면 setFriends() 로 통째로 다시 그린다.
 */
public class FriendListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final Map<String, Boolean> online = new HashMap<>();

    private final JLabel empty = new JLabel("<html><center>아직 친구가 없어요</center></html>", SwingConstants.CENTER);
    private final JButton chatButton = new JButton("대화하기");

    private Consumer<String> addHandler = nick -> { };
    private Consumer<String> chatHandler = nick -> { };

    public FriendListPanel() {
        setLayout(new BorderLayout(0, 6));
        setPreferredSize(new Dimension(180, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(0xFFF0F5));

        JLabel title = new JLabel("친구 목록");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        empty.setForeground(Color.GRAY);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                String nick = (String) value;
                boolean on = online.getOrDefault(nick, false);
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        l, (on ? "● " : "○ ") + nick, index, selected, focus);
                if (!selected) {
                    label.setForeground(on ? new Color(0x2E9E4F) : Color.GRAY);
                }
                return label;
            }
        });
        list.addListSelectionListener(e -> chatButton.setEnabled(list.getSelectedValue() != null));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openChat();
                }
            }
        });

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        center.add(empty, BorderLayout.SOUTH);

        chatButton.setEnabled(false);
        chatButton.setFocusable(false);
        chatButton.addActionListener(e -> openChat());

        JButton addButton = new JButton("친구 추가");
        addButton.setFocusable(false);
        addButton.addActionListener(e -> {
            String nick = JOptionPane.showInputDialog(this, "친구 신청할 닉네임을 입력하세요.", "친구 추가",
                    JOptionPane.PLAIN_MESSAGE);
            if (nick != null && !nick.trim().isEmpty()) {
                addHandler.accept(nick.trim());
            }
        });

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 4));
        buttons.setOpaque(false);
        buttons.add(chatButton);
        buttons.add(addButton);

        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void openChat() {
        String nick = list.getSelectedValue();
        if (nick != null) {
            chatHandler.accept(nick);
        }
    }

    /** [친구 추가]에서 닉네임을 입력하면 호출된다 (MainFrame 이 /FRIEND_REQ 를 보냄). */
    public void setAddHandler(Consumer<String> handler) {
        this.addHandler = handler;
    }

    /** [대화하기]를 누르면 호출된다 (MainFrame 이 1:1 방을 연다). */
    public void setChatHandler(Consumer<String> handler) {
        this.chatHandler = handler;
    }

    /** 친구 목록을 통째로 갈아 끼운다. 닉네임 -> 접속 여부. EDT 에서 호출할 것. */
    public void setFriends(Map<String, Boolean> friends) {
        String selected = list.getSelectedValue();
        online.clear();
        online.putAll(friends);
        model.clear();
        for (String nick : friends.keySet()) {
            model.addElement(nick);
        }
        if (selected != null && friends.containsKey(selected)) {
            list.setSelectedValue(selected, true);
        }
        empty.setVisible(model.isEmpty());
    }

    public DefaultListModel<String> getModel() {
        return model;
    }

    public JList<String> getList() {
        return list;
    }
}