package client;

import common.GrowthRule;
import common.PartCatalog;
import common.Pop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

/**
 * 집 화면 아래의 돌보기 버튼과 상태창.
 * 버튼을 누르면 서버로 보낼 행동(FEED/WASH/PET)을 넘겨주기만 하고, 결과는 서버가 /POP 으로 알려준다.
 *
 * 아이콘: icon/feed.png, icon/wash.png, icon/pet.png (16x16 권장) 가 있으면 버튼에 붙는다.
 */
public class CarePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JButton feedButton = makeButton("밥 주기", "feed");
    private final JButton washButton = makeButton("씻기기", "wash");
    private final JButton petButton = makeButton("쓰다듬기", "pet");

    private final JProgressBar hungerBar = makeBar(new Color(0xFFB37A));
    private final JProgressBar cleanBar = makeBar(new Color(0x8FCBFF));
    private final JProgressBar happyBar = makeBar(new Color(0xFF8FB8));
    private final JProgressBar expBar = makeBar(new Color(0xB59CFF));
    private final JLabel expLabel = new JLabel("성장");

    private transient Consumer<String> careSender = a -> { };

    public CarePanel() {
        setLayout(new BorderLayout(8, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        setBackground(new Color(0xFFF0F5));

        JPanel buttons = new JPanel(new GridLayout(1, 3, 8, 0));
        buttons.setOpaque(false);
        buttons.add(feedButton);
        buttons.add(washButton);
        buttons.add(petButton);

        feedButton.addActionListener(e -> careSender.accept(GrowthRule.FEED));
        washButton.addActionListener(e -> careSender.accept(GrowthRule.WASH));
        petButton.addActionListener(e -> careSender.accept(GrowthRule.PET));

        JPanel bars = new JPanel(new GridLayout(2, 4, 6, 2));
        bars.setOpaque(false);
        bars.add(new JLabel("배고픔"));
        bars.add(hungerBar);
        bars.add(new JLabel("청결"));
        bars.add(cleanBar);
        bars.add(new JLabel("행복"));
        bars.add(happyBar);
        bars.add(expLabel);
        bars.add(expBar);

        add(buttons, BorderLayout.NORTH);
        add(bars, BorderLayout.CENTER);
        setButtonsEnabled(false);
    }

    /** 버튼을 눌렀을 때 서버로 보내는 함수 연결 */
    public void setCareSender(Consumer<String> sender) {
        this.careSender = sender;
    }

    /** 서버에서 /POP 을 받을 때마다 호출 (EDT 에서) */
    public void setPop(Pop pop) {
        hungerBar.setValue(pop.hunger);
        cleanBar.setValue(pop.clean);
        happyBar.setValue(pop.happy);
        hungerBar.setString(pop.hunger + "");
        cleanBar.setString(pop.clean + "");
        happyBar.setString(pop.happy + "");

        int goal = pop.expGoal();
        if (goal > 0) {
            expBar.setMaximum(goal);
            expBar.setValue(pop.exp);
            expBar.setString(pop.exp + " / " + goal);
            expLabel.setText(pop.stage == Pop.Stage.EGG ? "부화" : "성장");
        } else {
            expBar.setMaximum(1);
            expBar.setValue(1);
            expBar.setString("다 자람");
            expLabel.setText("성장");
        }
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        feedButton.setEnabled(enabled);
        washButton.setEnabled(enabled);
        petButton.setEnabled(enabled);
    }

    private static JButton makeButton(String text, String iconName) {
        JButton b = new JButton(text);
        File f = PartCatalog.file("icon", iconName);
        if (f.isFile()) {
            try {
                BufferedImage img = javax.imageio.ImageIO.read(f);
                if (img != null) {
                    // 16x16 아이콘을 2배로 (도트 유지)
                    BufferedImage big = new BufferedImage(img.getWidth() * 2, img.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = big.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(img, 0, 0, big.getWidth(), big.getHeight(), null);
                    g.dispose();
                    b.setIcon(new ImageIcon(big));
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return b;
    }

    private static JProgressBar makeBar(Color color) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setForeground(color);
        return bar;
    }
}
