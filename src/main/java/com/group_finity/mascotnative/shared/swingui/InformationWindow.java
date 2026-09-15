package com.group_finity.mascotnative.shared.swingui;

import com.group_finity.mascot.Tr;
import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascot.imageset.ShimejiProgramFolder.InfoField;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The per image set credits splash shown the first time a set is used
 * (when its info.xml has a SplashImage).
 */
public final class InformationWindow {

    private InformationWindow() {}

    public static void show(ShimejiProgramFolder pf, String imageSet, Map<InfoField, String> info, Runnable onDismiss) {
        SwingUtilities.invokeLater(() -> buildAndShow(pf, imageSet, info, onDismiss));
    }

    private static void buildAndShow(ShimejiProgramFolder pf, String imageSet, Map<InfoField, String> info, Runnable onDismiss) {
        var frame = new JFrame(Tr.tr("About"));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                onDismiss.run();
            }
        });

        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        //--- splash image
        Path imgPath = resolveSplash(pf, imageSet, info);
        if (imgPath != null) {
            try {
                BufferedImage img = ImageIO.read(imgPath.toFile());
                if (img != null) {
                    int maxW = 360;
                    int maxH = 260;
                    double scale = Math.min(1.0, Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight()));
                    int w = (int) Math.round(img.getWidth() * scale);
                    int h = (int) Math.round(img.getHeight() * scale);
                    var scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    panel.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
                }
            } catch (Exception ignored) {
            }
        }

        //--- credits
        var lines = new ArrayList<String>();
        addLine(lines, info.get(InfoField.NAME) == null ? imageSet : info.get(InfoField.NAME));
        addCredit(lines, Tr.tr("ArtBy"), info.get(InfoField.ARTIST_NAME), info.get(InfoField.ARTIST_URL));
        addCredit(lines, Tr.tr("ScriptedBy"), info.get(InfoField.SCRIPTER_NAME), info.get(InfoField.SCRIPTER_URL));
        addCredit(lines, Tr.tr("CommissionedBy"), info.get(InfoField.COMMISSIONER_NAME), info.get(InfoField.COMMISSIONER_URL));
        addCredit(lines, Tr.tr("SupportAt"), info.get(InfoField.SUPPORT_NAME), info.get(InfoField.SUPPORT_URL));

        var text = new JTextArea(String.join("\n", lines));
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(UIManager.getFont("Label.font"));
        panel.add(text, BorderLayout.SOUTH);

        var closeBtn = new JButton(Tr.tr("Close"));
        closeBtn.addActionListener(e -> frame.dispose());
        var btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        var content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static Path resolveSplash(ShimejiProgramFolder pf, String imageSet, Map<InfoField, String> info) {
        var setPath = pf.imgPath().resolve(imageSet);
        for (String name : new String[]{info.get(InfoField.SPLASH_IMAGE), info.get(InfoField.PREVIEW_IMAGE)}) {
            if (name != null && !name.isBlank()) {
                var p = setPath.resolve(name);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        return pf.getIconPathForImageSet(imageSet);
    }

    private static void addLine(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    private static void addCredit(List<String> lines, String label, String name, String url) {
        if (name == null || name.isBlank()) {
            return;
        }
        lines.add(label + ": " + name + (url != null && !url.isBlank() ? "  (" + url + ")" : ""));
    }
}
