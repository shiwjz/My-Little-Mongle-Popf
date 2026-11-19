package client;

import common.Pop;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 보관함 창. 교배로 받은 알과 쉬고 있는 포프를 보여주고, [키우기]를 누르면 지금 포프와 바꾼다.
 * 실제로 바꾸는 건 서버가 하고(/SWITCH 번호), 결과는 /POP 과 /BOX 로 다시 온다.
 */
public class BoxDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int THUMB = 96;

    public BoxDialog(Frame owner, List<Pop> box, IntConsumer onRaise) {
        super(owner, "보관함 (" + box.size() + ")", true);

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        grid.setBackground(new Color(0xFFF5F8));

        if (box.isEmpty()) {
            JLabel empty = new JLabel("<html><center>보관함이 비어 있어요.<br>광장에서 다 자란 포프끼리 교배하면 알이 생겨요.</center></html>",
                    SwingConstants.CENTER);
            empty.setPreferredSize(new Dimension(360, 120));
            grid.setLayout(new BorderLayout());
            grid.add(empty, BorderLayout.CENTER);
        }

        for (int i = 0; i < box.size(); i++) {
            Pop p = box.get(i);
            final int index = i;

            JPanel cell = new JPanel(new BorderLayout(0, 4));
            cell.setBackground(Color.WHITE);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xF0C8D6)),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)));

            JLabel img = new JLabel(new ImageIcon(PopRenderer.render(p, THUMB)));
            JLabel label = new JLabel((i + 1) + ". " + p.stage.label, SwingConstants.CENTER);
            JButton raise = new JButton("키우기");
            raise.addActionListener(e -> {
                onRaise.accept(index);
                dispose();
            });

            cell.add(img, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout(0, 2));
            bottom.setOpaque(false);
            bottom.add(label, BorderLayout.NORTH);
            bottom.add(raise, BorderLayout.SOUTH);
            cell.add(bottom, BorderLayout.SOUTH);
            grid.add(cell);
        }

        JLabel hint = new JLabel("  [키우기]를 누르면 지금 키우는 포프는 보관함으로 들어가요.");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 6));

        add(new JScrollPane(grid), BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        pack();
        setSize(Math.min(getWidth() + 20, 520), Math.min(getHeight() + 10, 520));
        setLocationRelativeTo(owner);
    }
}
