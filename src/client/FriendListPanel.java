package client;

import javax.swing.*;
import java.awt.*;

/**
 * 메인 창 왼쪽의 친구 목록 자리.
 * 지금은 틀만 있고, 친구 추가·온라인 표시·대화하기는 4주차(정헌 담당)에 채운다.
 */
public class FriendListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    public FriendListPanel() {
        setLayout(new BorderLayout(0, 6));
        setPreferredSize(new Dimension(180, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(0xFFF0F5));

        JLabel title = new JLabel("친구 목록");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JLabel empty = new JLabel("<html><center>아직 친구가 없어요</center></html>", SwingConstants.CENTER);
        empty.setForeground(Color.GRAY);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        center.add(empty, BorderLayout.SOUTH);

        JButton addButton = new JButton("친구 추가");
        addButton.setEnabled(false);
        addButton.setToolTipText("4주차에 구현 예정");

        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(addButton, BorderLayout.SOUTH);
    }

    public DefaultListModel<String> getModel() {
        return model;
    }

    public JList<String> getList() {
        return list;
    }
}
