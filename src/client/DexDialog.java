package client;

import common.DexEntry;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 도감 창. 청혼에 성공해서 떠나보낸 포프들을 오래된 순으로 보여준다.
 * 보기만 하는 기록이라 다시 키울 수 없다. 목록은 서버가 /DEX 로 보내준다.
 */
public class DexDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int THUMB = 96;

    public DexDialog(Frame owner, List<DexEntry> dex) {
        super(owner, "도감 (" + dex.size() + ")", true);

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        grid.setBackground(new Color(0xFFF5F8));

        if (dex.isEmpty()) {
            JLabel empty = new JLabel("<html><center>아직 도감이 비어 있어요.<br>"
                    + "다 자란 포프가 광장에서 청혼에 성공하면 여기에 남아요.</center></html>", SwingConstants.CENTER);
            empty.setPreferredSize(new Dimension(380, 120));
            grid.setLayout(new BorderLayout());
            grid.add(empty, BorderLayout.CENTER);
        }

        for (int i = 0; i < dex.size(); i++) {
            DexEntry e = dex.get(i);
            JPanel cell = new JPanel(new BorderLayout(0, 4));
            cell.setBackground(Color.WHITE);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xF0C8D6)),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            cell.add(new JLabel(new ImageIcon(PopRenderer.render(e.toPop(), THUMB))), BorderLayout.CENTER);
            JLabel label = new JLabel("<html><center>" + (i + 1) + "대<br>짝: " + e.partner + "</center></html>",
                    SwingConstants.CENTER);
            cell.add(label, BorderLayout.SOUTH);
            grid.add(cell);
        }

        JLabel hint = new JLabel("  지금 키우는 포프는 " + (dex.size() + 1) + "대예요.");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 6));

        add(new JScrollPane(grid), BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        pack();
        setSize(Math.min(getWidth() + 20, 540), Math.min(getHeight() + 10, 540));
        setLocationRelativeTo(owner);
    }
}
