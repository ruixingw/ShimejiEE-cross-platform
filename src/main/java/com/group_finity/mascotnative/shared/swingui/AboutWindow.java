package com.group_finity.mascotnative.shared.swingui;

import com.group_finity.mascot.Tr;
import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascotapp.Constants;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.group_finity.mascot.imageset.ShimejiProgramFolder.InfoField.*;

/**
 * The about/credits window.
 * <p>
 * Shows the app credits along with the credits of the selected image sets
 * (from their {@code info.xml}), replacing the original InformationWindow.
 */
public final class AboutWindow {

    private AboutWindow() {}

    public static void show(ShimejiProgramFolder pf, Collection<String> imageSets) {
        SwingUtilities.invokeLater(() -> buildAndShow(pf, imageSets));
    }

    private static void buildAndShow(ShimejiProgramFolder pf, Collection<String> imageSets) {
        var frame = new JFrame(Tr.tr("About"));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        frame.setContentPane(createContent(pf, imageSets));
        frame.setSize(420, 520);
        frame.setLocationRelativeTo(null);

        var closeBtn = new JButton(Tr.tr("Close"));
        closeBtn.addActionListener(e -> frame.dispose());
        var btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        var content = new JPanel(new BorderLayout());
        content.add(frame.getContentPane(), BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);
        frame.setContentPane(content);

        frame.setVisible(true);
    }

    /**
     * The credits content, reusable inside other windows (eg the settings about tab).
     */
    public static JComponent createContent(ShimejiProgramFolder pf, Collection<String> imageSets) {
        var pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText(buildHtml(pf, imageSets));
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                browse(e.getURL());
            }
        });
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setMargin(new Insets(12, 16, 12, 16));
        return new JScrollPane(pane);
    }

    private static String buildHtml(ShimejiProgramFolder pf, Collection<String> imageSets) {
        var sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif'>");

        sb.append("<h2>ShimejiEE <small>").append(Constants.APP_VERSION).append("</small></h2>");
        sb.append("<p>").append(escape(Tr.tr("AboutTagline"))).append("</p><hr>");

        sb.append("<h3>").append(Tr.tr("About")).append("</h3><p>");
        sb.append(credit("LavenderSnek", Tr.tr("AboutMacMaintainer"), "https://github.com/LavenderSnek/ShimejiEE-cross-platform"));
        sb.append("<br>").append(credit("Kilkakon", Tr.tr("AboutKilkakonWork"), "http://kilkakon.com/shimeji"));
        sb.append("<br>").append(credit("nonowarn", Tr.tr("AboutNonowarnWork"), "https://github.com/nonowarn/shimeji4mac"));
        sb.append("<br>").append(credit("TigerHix", Tr.tr("AboutTigerHixWork"), "https://github.com/TigerHix/shimeji-universal"));
        sb.append("<br>").append("The shimeji-ee Group — ").append(escape(Tr.tr("AboutShimejiEeGroupWork")));
        sb.append("<br>").append("Group Finity — ").append(escape(Tr.tr("AboutGroupFinityWork")));
        sb.append("</p>");

        if (pf != null && imageSets != null && !imageSets.isEmpty()) {
            for (String set : imageSets) {
                var info = pf.readInfoFile(set);
                sb.append("<hr><h3>").append(escape(displayName(set, info))).append("</h3>");

                String preview = info == null ? null : info.get(ShimejiProgramFolder.InfoField.PREVIEW_IMAGE);
                if (preview != null) {
                    // images are resolved through the html kit's base later; keep it simple with a file url
                    Path imgDir = pf.imgPath().resolve(set);
                    Path previewPath = imgDir.resolve(preview);
                    if (!Files.isRegularFile(previewPath)) {
                        previewPath = pf.getIconPathForImageSet(set);
                    }
                    if (previewPath != null && Files.isRegularFile(previewPath)) {
                        sb.append("<img src='").append(previewPath.toUri()).append("' width='96' style='float:right;margin-left:10px'>");
                    }
                }

                sb.append(creditsHtml(info));
                sb.append("<p style='clear:both'></p>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String displayName(String set, java.util.Map<ShimejiProgramFolder.InfoField, String> info) {
        if (info != null) {
            String name = info.get(ShimejiProgramFolder.InfoField.NAME);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return set;
    }

    private static String creditsHtml(java.util.Map<ShimejiProgramFolder.InfoField, String> info) {
        var credits = new ArrayList<String>();
        if (info != null) {
            addCredit(credits, Tr.tr("ArtBy"), info.get(ARTIST_NAME), info.get(ARTIST_URL));
            addCredit(credits, Tr.tr("ScriptedBy"), info.get(SCRIPTER_NAME), info.get(SCRIPTER_URL));
            addCredit(credits, Tr.tr("CommissionedBy"), info.get(COMMISSIONER_NAME), info.get(COMMISSIONER_URL));
            addCredit(credits, Tr.tr("SupportAt"), info.get(SUPPORT_NAME), info.get(SUPPORT_URL));
        }
        if (credits.isEmpty()) {
            return "";
        }
        return "<p>" + String.join("<br>", credits) + "</p>";
    }

    private static void addCredit(List<String> out, String label, String name, String url) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (url != null && !url.isBlank()) {
            out.add(escape(label) + " <a href='" + escape(url) + "'>" + escape(name) + "</a>");
        } else {
            out.add(escape(label) + " " + escape(name));
        }
    }

    private static String credit(String name, String role, String url) {
        return "<a href='" + escape(url) + "'>" + escape(name) + "</a> — " + escape(role);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    private static void browse(java.net.URL url) {
        if (url == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(url.toURI());
        } catch (Exception ignored) {
        }
    }
}
