package com.group_finity.mascotnative.shared.swingui;

import com.group_finity.mascot.Tr;
import com.group_finity.mascot.environment.WindowTitleFilter;
import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascotapp.prefs.MutablePrefs;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * The full settings window with tabs (general, interactive windows, window
 * mode and about), modeled after the original SettingsWindow.
 * <p>
 * The original theme tab is not included since this version has no themable
 * look and feel.
 */
public final class SettingsWindow {

    private final MutablePrefs prefs;

    private SettingsWindow(MutablePrefs prefs) {
        this.prefs = prefs;
    }

    /**
     * @param onApply called after the prefs have been written when the user hits apply.
     */
    public static void show(MutablePrefs prefs, ShimejiProgramFolder pf, Collection<String> imageSets, Runnable onApply) {
        SwingUtilities.invokeLater(() -> new SettingsWindow(prefs).buildAndShow(pf, imageSets, onApply));
    }

    private void buildAndShow(ShimejiProgramFolder pf, Collection<String> imageSets, Runnable onApply) {
        var frame = new JFrame(Tr.tr("Settings"));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        var tabs = new JTabbedPane();
        tabs.addTab(Tr.tr("General"), buildGeneralTab());
        tabs.addTab(Tr.tr("InteractiveWindows"), buildInteractiveTab());
        tabs.addTab(Tr.tr("WindowMode"), buildWindowModeTab());
        tabs.addTab(Tr.tr("About"), AboutWindow.createContent(pf, imageSets));

        var applyBtn = new JButton(Tr.tr("Apply"));
        var closeBtn = new JButton(Tr.tr("Close"));
        applyBtn.addActionListener(e -> {
            writeBackGeneral();
            writeBackInteractive();
            writeBackWindowMode();
            onApply.run();
        });
        closeBtn.addActionListener(e -> frame.dispose());

        var btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(applyBtn);
        btnPanel.add(closeBtn);

        var content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        content.add(tabs, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setSize(460, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    //========== General ==========//

    private JSpinner scalingSpinner;
    private JSlider opacitySlider;
    private JLabel opacityLabel;
    private JCheckBox breedingCheck;
    private JCheckBox transientCheck;
    private JCheckBox transformCheck;
    private JCheckBox throwingCheck;
    private JCheckBox soundsCheck;
    private JCheckBox multiscreenCheck;
    private JCheckBox translateCheck;
    private JCheckBox alwaysChooserCheck;
    private JCheckBox alwaysInfoCheck;
    private JCheckBox ignoreImagesetPropsCheck;
    private JTextField nameOverrideField;
    private JCheckBox logicalAnchorsCheck;
    private JCheckBox asymmetryCheck;
    private JCheckBox fixSoundCheck;
    private JRadioButton nearestRadio;
    private JRadioButton bicubicRadio;
    private JRadioButton hqxRadio;

    private JComponent buildGeneralTab() {
        var panel = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(3, 3, 3, 3);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        //--- scaling
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("Scaling") + ":"), gc);
        var scalingGc = (GridBagConstraints) gc.clone();
        scalingGc.gridx = 1;
        scalingGc.gridwidth = 1;
        scalingSpinner = new JSpinner(new SpinnerNumberModel(prefs.Scaling, 0.1, 8.0, 0.1));
        panel.add(scalingSpinner, scalingGc);

        //--- opacity
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("Opacity") + ":"), gc);
        var opacityGc = (GridBagConstraints) gc.clone();
        opacityGc.gridx = 1;
        opacityGc.gridwidth = 1;
        opacitySlider = new JSlider(10, 100, (int) Math.round(prefs.Opacity * 100));
        opacitySlider.setPaintTicks(true);
        opacitySlider.setMajorTickSpacing(30);
        opacityLabel = new JLabel(opacitySlider.getValue() + "%");
        opacitySlider.addChangeListener(e -> opacityLabel.setText(opacitySlider.getValue() + "%"));
        var opacityBox = Box.createHorizontalBox();
        opacityBox.add(opacitySlider);
        opacityBox.add(Box.createHorizontalStrut(6));
        opacityBox.add(opacityLabel);
        panel.add(opacityBox, opacityGc);

        //--- filter
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("FilterOptions") + ":"), gc);
        var filterGc = (GridBagConstraints) gc.clone();
        filterGc.gridx = 1;
        filterGc.gridwidth = 1;
        var filterGroup = new ButtonGroup();
        nearestRadio = new JRadioButton(Tr.tr("NearestNeighbour"), prefs.PixelArtScaling && !prefs.HqxScaling);
        bicubicRadio = new JRadioButton(Tr.tr("BicubicFilter"), !prefs.PixelArtScaling && !prefs.HqxScaling);
        hqxRadio = new JRadioButton("hqx", prefs.HqxScaling);
        filterGroup.add(nearestRadio);
        filterGroup.add(bicubicRadio);
        filterGroup.add(hqxRadio);
        var filterBox = Box.createHorizontalBox();
        filterBox.add(nearestRadio);
        filterBox.add(bicubicRadio);
        filterBox.add(hqxRadio);
        panel.add(filterBox, filterGc);

        //--- toggles
        gc.gridy = row++;
        panel.add(sectionLabel(Tr.tr("AllowedBehaviours")), gc);
        gc.gridy = row++;
        breedingCheck = check(Tr.tr("BreedingCloning"), prefs.Breeding);
        transientCheck = check(Tr.tr("BreedingTransient"), prefs.Transients);
        transformCheck = check(Tr.tr("Transformation"), prefs.Transformation);
        throwingCheck = check(Tr.tr("ThrowingWindows"), prefs.Throwing);
        soundsCheck = check(Tr.tr("SoundEffects"), prefs.Sounds);
        multiscreenCheck = check(Tr.tr("Multiscreen"), prefs.Multiscreen);
        panel.add(twoColumns(breedingCheck, transientCheck, transformCheck, throwingCheck, soundsCheck, multiscreenCheck), gc);

        gc.gridy = row++;
        translateCheck = check(Tr.tr("TranslateBehaviorNames"), prefs.TranslateBehaviorNames);
        alwaysChooserCheck = check(Tr.tr("AlwaysShowShimejiChooser"), prefs.AlwaysShowShimejiChooser);
        alwaysInfoCheck = check(Tr.tr("AlwaysShowInformationScreen"), prefs.AlwaysShowInformationScreen);
        ignoreImagesetPropsCheck = check(Tr.tr("IgnoreImagesetProperties"), prefs.IgnoreImagesetProperties);
        panel.add(twoColumns(translateCheck, alwaysChooserCheck, alwaysInfoCheck, ignoreImagesetPropsCheck), gc);

        gc.gridy = row++;
        panel.add(sectionLabel(Tr.tr("Settings")), gc);
        gc.gridy = row++;
        nameOverrideField = new JTextField(prefs.ShimejiEENameOverride == null ? "" : prefs.ShimejiEENameOverride, 16);
        var nameBox = Box.createHorizontalBox();
        nameBox.add(new JLabel(Tr.tr("ShimejiEENameOverride") + ":"));
        nameBox.add(Box.createHorizontalStrut(6));
        nameBox.add(nameOverrideField);
        panel.add(nameBox, gc);

        gc.gridy = row++;
        panel.add(sectionLabel(Tr.tr("ImageSet")), gc);
        gc.gridy = row++;
        logicalAnchorsCheck = check(Tr.tr("LogicalAnchors"), prefs.LogicalAnchors);
        asymmetryCheck = check(Tr.tr("AsymmetryNameScheme"), prefs.AsymmetryNameScheme);
        fixSoundCheck = check(Tr.tr("FixRelativeGlobalSound"), prefs.FixRelativeGlobalSound);
        panel.add(twoColumns(logicalAnchorsCheck, asymmetryCheck, fixSoundCheck), gc);

        gc.gridy = row;
        var needsReload = new JLabel(Tr.tr("NeedsReload"));
        needsReload.setFont(needsReload.getFont().deriveFont(Font.ITALIC, needsReload.getFont().getSize() - 1f));
        panel.add(needsReload, gc);

        var scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JComponent sectionLabel(String text) {
        var label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        return label;
    }

    private static JCheckBox check(String text, boolean value) {
        return new JCheckBox(text, value);
    }

    /** two columns of checkboxes in a grid */
    private static JComponent twoColumns(JCheckBox... boxes) {
        int cols = 2;
        int rows = (boxes.length + 1) / 2;
        var panel = new JPanel(new GridLayout(rows, cols, 4, 2));
        for (JCheckBox box : boxes) {
            panel.add(box);
        }
        while (panel.getComponentCount() < rows * cols) {
            panel.add(new JPanel());
        }
        return panel;
    }

    private void writeBackGeneral() {
        try {
            prefs.Scaling = ((Number) scalingSpinner.getValue()).doubleValue();
        } catch (Exception ignored) {
        }
        prefs.Opacity = opacitySlider.getValue() / 100.0;

        prefs.PixelArtScaling = nearestRadio.isSelected() || hqxRadio.isSelected();
        prefs.HqxScaling = hqxRadio.isSelected();

        prefs.Breeding = breedingCheck.isSelected();
        prefs.Transients = transientCheck.isSelected();
        prefs.Transformation = transformCheck.isSelected();
        prefs.Throwing = throwingCheck.isSelected();
        prefs.Sounds = soundsCheck.isSelected();
        prefs.Multiscreen = multiscreenCheck.isSelected();
        prefs.TranslateBehaviorNames = translateCheck.isSelected();
        prefs.AlwaysShowShimejiChooser = alwaysChooserCheck.isSelected();
        prefs.AlwaysShowInformationScreen = alwaysInfoCheck.isSelected();
        prefs.IgnoreImagesetProperties = ignoreImagesetPropsCheck.isSelected();
        prefs.ShimejiEENameOverride = nameOverrideField.getText().trim();
        prefs.LogicalAnchors = logicalAnchorsCheck.isSelected();
        prefs.AsymmetryNameScheme = asymmetryCheck.isSelected();
        prefs.FixRelativeGlobalSound = fixSoundCheck.isSelected();
    }

    //========== Interactive windows ==========//

    private JTextArea whitelistArea;
    private JTextArea blacklistArea;

    private JComponent buildInteractiveTab() {
        var tabs = new JTabbedPane();
        whitelistArea = captionArea(prefs.InteractiveWindows);
        blacklistArea = captionArea(prefs.InteractiveWindowsBlacklist);
        tabs.addTab(Tr.tr("Whitelist"), wrapArea(whitelistArea));
        tabs.addTab(Tr.tr("Blacklist"), wrapArea(blacklistArea));
        return tabs;
    }

    private static JTextArea captionArea(String raw) {
        var area = new JTextArea(8, 24);
        area.setLineWrap(true);
        if (raw != null && !raw.isBlank()) {
            area.setText(String.join("\n", WindowTitleFilter.parse(raw)));
        }
        return area;
    }

    private static JComponent wrapArea(JTextArea area) {
        var scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(new JLabel(" " + Tr.tr("InteractiveWindowHintMessage")), BorderLayout.SOUTH);
        return panel;
    }

    private void writeBackInteractive() {
        prefs.InteractiveWindows = joinCaptions(whitelistArea.getText());
        prefs.InteractiveWindowsBlacklist = joinCaptions(blacklistArea.getText());
    }

    private static String joinCaptions(String text) {
        return String.join("/", WindowTitleFilter.parse(text == null ? "" : text.replace('\n', '/')));
    }

    //========== Window mode ==========//

    private JSpinner windowWidthSpinner;
    private JSpinner windowHeightSpinner;
    private JLabel colorPreview;
    private Color backgroundColor;
    private JTextField backgroundImageField;
    private JComboBox<String> backgroundModeCombo;
    private String[] backgroundModeKeys;
    private JCheckBox useWindowModeCheck;

    private JComponent buildWindowModeTab() {
        var panel = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(3, 3, 3, 3);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        var size = parseSizeOr(prefs.WindowSize, 900, 600);

        //--- window mode toggle
        gc.gridy = row++;
        useWindowModeCheck = check(Tr.tr("UseWindowMode"), "virtualdesktop".equals(prefs.Environment == null ? "" : prefs.Environment.trim()));
        panel.add(useWindowModeCheck, gc);

        //--- window size
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("WindowSize") + ":"), gc);
        var sizeGc = (GridBagConstraints) gc.clone();
        sizeGc.gridx = 1;
        windowWidthSpinner = new JSpinner(new SpinnerNumberModel(size[0], 200, 7680, 10));
        windowHeightSpinner = new JSpinner(new SpinnerNumberModel(size[1], 200, 4320, 10));
        var sizeBox = Box.createHorizontalBox();
        sizeBox.add(windowWidthSpinner);
        sizeBox.add(new JLabel(" x "));
        sizeBox.add(windowHeightSpinner);
        panel.add(sizeBox, sizeGc);

        //--- background colour
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("Background") + ":"), gc);
        var colorGc = (GridBagConstraints) gc.clone();
        colorGc.gridx = 1;
        backgroundColor = parseColorOr(prefs.Background, Color.WHITE);
        colorPreview = new JLabel("     ");
        colorPreview.setOpaque(true);
        colorPreview.setBackground(backgroundColor);
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        var colorBtn = new JButton(Tr.tr("ChooseBackgroundColour"));
        colorBtn.addActionListener(e -> {
            var chosen = JColorChooser.showDialog(colorBtn, Tr.tr("ChooseBackgroundColour"), backgroundColor);
            if (chosen != null) {
                backgroundColor = chosen;
                colorPreview.setBackground(chosen);
            }
        });
        var colorBox = Box.createHorizontalBox();
        colorBox.add(colorPreview);
        colorBox.add(Box.createHorizontalStrut(8));
        colorBox.add(colorBtn);
        panel.add(colorBox, colorGc);

        //--- background image
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("BackgroundImage") + ":"), gc);
        var imageGc = (GridBagConstraints) gc.clone();
        imageGc.gridx = 1;
        backgroundImageField = new JTextField(prefs.BackgroundImage == null ? "" : prefs.BackgroundImage, 18);
        var browseBtn = new JButton(Tr.tr("Browse"));
        browseBtn.addActionListener(e -> {
            var chooser = new JFileChooser();
            chooser.setDialogTitle(Tr.tr("ChooseBackgroundImage"));
            var current = backgroundImageField.getText();
            if (!current.isBlank()) {
                var f = java.nio.file.Path.of(current.trim()).toAbsolutePath().toFile();
                if (f.isFile()) {
                    chooser.setSelectedFile(f);
                }
            }
            if (chooser.showOpenDialog(browseBtn) == JFileChooser.APPROVE_OPTION) {
                backgroundImageField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        var removeBtn = new JButton(Tr.tr("Remove"));
        removeBtn.addActionListener(e -> backgroundImageField.setText(""));
        var imageBox = Box.createHorizontalBox();
        imageBox.add(backgroundImageField);
        imageBox.add(Box.createHorizontalStrut(4));
        imageBox.add(browseBtn);
        imageBox.add(removeBtn);
        panel.add(imageBox, imageGc);

        //--- background mode
        gc.gridy = row++;
        panel.add(new JLabel(Tr.tr("BackgroundMode") + ":"), gc);
        var modeGc = (GridBagConstraints) gc.clone();
        modeGc.gridx = 1;
        var modes = new String[]{Tr.tr("BackgroundModeCentre"), Tr.tr("BackgroundModeFill"), Tr.tr("BackgroundModeFit"), Tr.tr("BackgroundModeStretch")};
        backgroundModeKeys = new String[]{"centre", "fill", "fit", "stretch"};
        var currentMode = prefs.BackgroundMode == null ? "centre" : prefs.BackgroundMode;
        int modeIdx = java.util.List.of(backgroundModeKeys).indexOf(currentMode);
        if (modeIdx < 0) modeIdx = 0;
        backgroundModeCombo = new JComboBox<>(modes);
        backgroundModeCombo.setSelectedIndex(modeIdx);
        panel.add(backgroundModeCombo, modeGc);

        var spacer = new JPanel(new GridBagLayout());
        spacer.add(panel);
        return spacer;
    }

    private void writeBackWindowMode() {
        prefs.Environment = useWindowModeCheck.isSelected() ? "virtualdesktop" : "";
        prefs.WindowSize = windowWidthSpinner.getValue() + "x" + windowHeightSpinner.getValue();
        prefs.Background = String.format("#%06X", (0xFFFFFF & backgroundColor.getRGB()));
        prefs.BackgroundImage = backgroundImageField.getText().trim();
        prefs.BackgroundMode = backgroundModeKeys[backgroundModeCombo.getSelectedIndex()];
    }

    private static int[] parseSizeOr(String raw, int defW, int defH) {
        try {
            String[] parts = raw.toLowerCase().split("x");
            if (parts.length == 2) {
                return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
            }
        } catch (Exception ignored) {
        }
        return new int[]{defW, defH};
    }

    private static Color parseColorOr(String hex, Color fallback) {
        try {
            if (hex != null && hex.startsWith("#") && hex.length() == 7) {
                return new Color((int) Long.parseLong(hex.substring(1), 16));
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
