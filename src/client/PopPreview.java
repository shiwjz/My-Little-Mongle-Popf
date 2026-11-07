package client;

import common.Gene;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 파츠 겹쳐 그리기 테스트용 창 (서버 없이 실행).
 * "랜덤 생성"을 누르면 Gene.random()으로 새 유전자를 만들고, 유전자 문자열과 그림을 보여준다.
 *
 * 실행: java -cp out client.PopPreview
 */
public class PopPreview extends JFrame {

    private static final long serialVersionUID = 1L;

    private final Random rnd = new Random();
    private final JLabel imageLabel = new JLabel();
    private final JTextArea geneText = new JTextArea(3, 40);
    private Gene gene;

    public PopPreview() {
        super("포프 미리보기 (파츠 겹쳐 그리기 테스트)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(320, 320));

        geneText.setEditable(false);
        geneText.setLineWrap(true);

        JButton randomButton = new JButton("랜덤 생성");
        randomButton.addActionListener(e -> {
            gene = Gene.random(rnd);
            refresh();
        });

        JButton colorButton = new JButton("색만 바꾸기");
        colorButton.addActionListener(e -> {
            gene.randomColors(rnd);
            refresh();
        });

        JPanel buttons = new JPanel();
        buttons.add(randomButton);
        buttons.add(colorButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JScrollPane(geneText), BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        add(imageLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        gene = Gene.random(rnd);
        refresh();

        pack();
        setLocationRelativeTo(null);
    }

    private void refresh() {
        BufferedImage img = PopRenderer.render(gene, 320);
        imageLabel.setIcon(new ImageIcon(img));
        geneText.setText(gene.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PopPreview().setVisible(true));
    }
}
