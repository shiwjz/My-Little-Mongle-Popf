package client;

import common.Gene;
import common.Pop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 파츠 겹쳐 그리기 테스트용 창 (서버 없이 실행).
 * - 랜덤 생성: 새 유전자
 * - 색만 바꾸기: 파츠는 그대로, 테마 색만 다시
 * - 알 / 아기 / 성체: 단계별 그림 확인 (아기 전용 그림이 없으면 성체를 줄인 임시 그림)
 * - 파일 다시 읽기: 파츠 PNG 를 바꾼 뒤 창을 끄지 않고 다시 보기
 *
 * 실행: java -cp out client.PopPreview
 */
public class PopPreview extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient Random rnd = new Random();
    private final JLabel imageLabel = new JLabel();
    private final JTextArea geneText = new JTextArea(3, 40);
    private final JComboBox<Pop.Stage> stageBox = new JComboBox<>(Pop.Stage.values());
    private transient Pop pop;

    public PopPreview() {
        super("포프 미리보기 (파츠 겹쳐 그리기 테스트)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(320, 320));
        imageLabel.setOpaque(true);
        imageLabel.setBackground(new Color(0xFFF5F8));

        geneText.setEditable(false);
        geneText.setLineWrap(true);

        JButton randomButton = new JButton("랜덤 생성");
        randomButton.addActionListener(e -> {
            pop.gene = Gene.random(rnd);
            refresh();
        });

        JButton colorButton = new JButton("색만 바꾸기");
        colorButton.addActionListener(e -> {
            pop.gene.randomColors(rnd);
            refresh();
        });

        JButton reloadButton = new JButton("파일 다시 읽기");
        reloadButton.addActionListener(e -> {
            PopRenderer.clearCache();
            refresh();
        });

        stageBox.setSelectedItem(Pop.Stage.ADULT);
        stageBox.addActionListener(e -> {
            pop.stage = (Pop.Stage) stageBox.getSelectedItem();
            refresh();
        });

        JPanel buttons = new JPanel();
        buttons.add(randomButton);
        buttons.add(colorButton);
        buttons.add(stageBox);
        buttons.add(reloadButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JScrollPane(geneText), BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        add(imageLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        pop = new Pop(Gene.random(rnd));
        pop.stage = Pop.Stage.ADULT;
        refresh();

        pack();
        setLocationRelativeTo(null);
    }

    private void refresh() {
        BufferedImage img = PopRenderer.render(pop, 320);
        imageLabel.setIcon(new ImageIcon(img));
        geneText.setText(pop.gene.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PopPreview().setVisible(true));
    }
}
