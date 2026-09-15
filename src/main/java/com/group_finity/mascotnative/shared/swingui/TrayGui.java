package com.group_finity.mascotnative.shared.swingui;

import com.group_finity.mascot.Tr;
import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascotapp.Constants;
import com.group_finity.mascotapp.Controller;
import com.group_finity.mascot.ui.NativeUi;
import com.group_finity.mascotnative.shared.swingui.chooser.ImageSetChooserUtils;
import com.group_finity.mascotapp.prefs.MutablePrefs;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrayGui implements NativeUi {
    private static final Logger log = Logger.getLogger(TrayGui.class.getName());

    public TrayGui(Controller controller) {
        this.controller = controller;
    }

    private final Controller controller;
    private MutablePrefs prefs;
    private TrayIcon trayIcon;

    @Override
    public void start(MutablePrefs prefs, Runnable onFinish) {
        this.prefs = prefs;
        try {
            SwingUtilities.invokeAndWait(this::createTrayIcon);
            onFinish.run();
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reload() {
        // rebuild the tray menu (with the new translations)
        SwingUtilities.invokeLater(() -> {
            removeTrayIcon();
            if (prefs != null) {
                createTrayIcon();
            }
        });
    }

    private void removeTrayIcon() {
        if (!SystemTray.isSupported()) {
            return;
        }
        TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
        for (TrayIcon icon : icons) {
            SystemTray.getSystemTray().remove(icon);
        }
        trayIcon = null;
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(null, message, Tr.tr("Error"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean askYesNo(String title, String message) {
        return JOptionPane.showConfirmDialog(null, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }


    @Override
    public void requestImageSetChooser(Collection<String> currentSelection, ShimejiProgramFolder pf) {
        ImageSetChooserUtils.askUserForSelection(controller::setImageSets, currentSelection, pf);
    }

    private static CheckboxMenuItem awtToggle(String text, BooleanSupplier getter, Consumer<Boolean> setter) {
        final var toggleBtn = new CheckboxMenuItem(text, getter.getAsBoolean());
        toggleBtn.addItemListener(e -> {
            setter.accept(!getter.getAsBoolean());
            toggleBtn.setState(getter.getAsBoolean());
        });
        return toggleBtn;
    }

    private MenuItem awtActionBtn(String title, String action) {
        final MenuItem btn = new MenuItem(title);
        btn.addActionListener(e -> controller.runGlobalAction(action));
        return btn;
    }

    //--- tray icon choices (img/icons/*.png, a random one on startup)

    private Path chosenTrayIcon;

    private Menu buildTrayIconMenu() {
        var iconMenu = new Menu(Tr.tr("TrayIcon"));

        var choices = getTrayIconChoices();
        Path active = activeTrayIcon(choices);

        for (Path iconPath : choices) {
            String name = iconPath.getFileName().toString().replaceFirst("(?i)\\.png$", "");
            final var btn = new MenuItem(name);
            btn.setEnabled(!iconPath.equals(active));
            btn.addActionListener(e -> {
                chosenTrayIcon = iconPath;
                applyTrayIcon(iconPath);
                for (int i = 0; i < iconMenu.getItemCount(); i++) {
                    iconMenu.getItem(i).setEnabled(true);
                }
                btn.setEnabled(false);
            });
            iconMenu.add(btn);
        }

        if (choices.isEmpty()) {
            var none = new MenuItem(Tr.tr("NeedsReload"));
            none.setEnabled(false);
            iconMenu.add(none);
        }

        return iconMenu;
    }

    private List<Path> getTrayIconChoices() {
        try {
            return controller.getProgramFolder().getTrayIconChoices();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * The icon to use right now: the user's session choice, else a random
     * one of the available choices (re-picked on every startup/reload).
     */
    private Path activeTrayIcon(List<Path> choices) {
        if (chosenTrayIcon != null && choices.contains(chosenTrayIcon)) {
            return chosenTrayIcon;
        }
        if (choices.isEmpty()) {
            return null;
        }
        return choices.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(choices.size()));
    }

    private void applyTrayIcon(Path iconPath) {
        if (trayIcon == null || iconPath == null) {
            return;
        }
        try {
            var img = ImageIO.read(iconPath.toFile());
            if (img != null) {
                trayIcon.setImage(img);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "unable to load tray icon " + iconPath, e);
        }
    }

    private void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            return;
        }

        //--languages submenu
        final Menu languageMenu = new Menu(Tr.tr("Language"));
        for (String[] lang : Constants.LANGUAGE_TABLE) {
            final var langName = lang[0];
            final var locale = Locale.forLanguageTag(lang[1]);
            final var langBtn = new MenuItem(langName);
            langBtn.addActionListener(e -> controller.setLocale(locale));
            languageMenu.add(langBtn);
        }

        //--scaling submenu
        final Menu scalingMenu = new Menu(Tr.tr("Scaling"));
        final double scalingStep = 0.25;
        final int scalesCount = 16;
        for (int i = 1; i <= scalesCount; i++) {
            double opt = i * scalingStep;
            final var scaleBtn = new MenuItem(String.valueOf(opt));

            if (prefs.Scaling == opt) {
                scaleBtn.setEnabled(false);
            }

            int btnIdx = i - 1;
            scaleBtn.addActionListener(_ -> {
                for (int j = 0; j < scalingMenu.getItemCount(); j++) {
                    scalingMenu.getItem(j).setEnabled(true);
                }
                prefs.Scaling = opt;
                controller.reloadImageSets();
                scalingMenu.getItem(btnIdx).setEnabled(false);
            });
            scalingMenu.add(scaleBtn);
        }

        //--behaviour toggles submenu
        final Menu bvTogglesMenu = new Menu(Tr.tr("AllowedBehaviours"));

        bvTogglesMenu.add(awtToggle(Tr.tr("BreedingCloning"), () -> prefs.Breeding, b -> prefs.Breeding = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("BreedingTransient"), () -> prefs.Transients, b -> prefs.Transients = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("Transformation"), () -> prefs.Transformation, b -> prefs.Transformation = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("ThrowingWindows"), () -> prefs.Throwing, b -> prefs.Throwing = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("SoundEffects"), () -> prefs.Sounds, b -> prefs.Sounds = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("Multiscreen"), () -> prefs.Multiscreen, b -> prefs.Multiscreen = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("TranslateBehaviorNames"), () -> prefs.TranslateBehaviorNames, b -> prefs.TranslateBehaviorNames = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("AlwaysShowShimejiChooser"), () -> prefs.AlwaysShowShimejiChooser, b -> prefs.AlwaysShowShimejiChooser = b));
        bvTogglesMenu.add(awtToggle(Tr.tr("IgnoreImagesetProperties"), () -> prefs.IgnoreImagesetProperties, b -> prefs.IgnoreImagesetProperties = b));

        //--image set toggles
        final Menu imgTogglesMenu = new Menu(Tr.tr("ImageSet"));

        var rl = new MenuItem(Tr.tr("NeedsReload"));
        rl.setEnabled(false);
        imgTogglesMenu.add(rl);

        imgTogglesMenu.add("-");

        imgTogglesMenu.add(awtToggle(Tr.tr("LogicalAnchors"), () -> prefs.LogicalAnchors, b -> prefs.LogicalAnchors = b));
        imgTogglesMenu.add(awtToggle(Tr.tr("AsymmetryNameScheme"),() -> prefs.AsymmetryNameScheme, b -> prefs.AsymmetryNameScheme = b));
        imgTogglesMenu.add(awtToggle(Tr.tr("PixelArtScaling"),() -> prefs.PixelArtScaling, b -> prefs.PixelArtScaling = b));
        imgTogglesMenu.add(awtToggle(Tr.tr("HqxScaling"),() -> prefs.HqxScaling, b -> prefs.HqxScaling = b));
        imgTogglesMenu.add(awtToggle(Tr.tr("FixRelativeGlobalSound"), () -> prefs.FixRelativeGlobalSound, b -> prefs.FixRelativeGlobalSound = b));

        //----------------------//

        //----Create pop-up menu-----//
        final PopupMenu trayPopup = new PopupMenu();

        trayPopup.add(awtActionBtn(Tr.tr("CallShimeji"), "CallShimeji"));
        trayPopup.add(awtActionBtn(Tr.tr("FollowCursor"), "FollowCursor"));
        trayPopup.add(awtActionBtn(Tr.tr("ReduceToOne"), "ReduceToOne"));
        trayPopup.add(awtActionBtn(Tr.tr("RestoreWindows"), "RestoreWindows"));

        trayPopup.add("-");

        trayPopup.add(languageMenu);
        trayPopup.add(scalingMenu);
        trayPopup.add(buildTrayIconMenu());
        trayPopup.add(bvTogglesMenu);
        trayPopup.add(imgTogglesMenu);

        trayPopup.add("-");

        trayPopup.add(awtActionBtn(Tr.tr("ChooseShimeji"), "ChooseShimeji"));
        trayPopup.add(awtActionBtn(Tr.tr("ReloadMascots"), "ReloadMascots"));
        trayPopup.add(awtActionBtn(Tr.tr("Settings"), "Settings"));
        trayPopup.add("-");
        trayPopup.add(awtActionBtn(Tr.tr("PauseAnimations"), "PauseAnimations"));
        trayPopup.add(awtActionBtn(Tr.tr("ResumeAnimations"), "ResumeAnimations"));
        trayPopup.add(awtActionBtn(Tr.tr("About"), "About"));
        trayPopup.add(awtActionBtn(Tr.tr("DismissAll"), "DismissAll"));
        trayPopup.add(awtActionBtn(Tr.tr("Quit"), "Quit"));

        try {
            //adding the tray icon

            Image trayIconImg = null;

            // a random icon from img/icons on startup (or the session choice after a reload)
            Path activeIcon = activeTrayIcon(getTrayIconChoices());
            if (activeIcon != null) {
                try {
                    trayIconImg = ImageIO.read(activeIcon.toFile());
                } catch (Exception e) {
                    log.log(Level.WARNING, "unable to load tray icon " + activeIcon, e);
                }
            }

            if (trayIconImg == null) {
                try {
                    trayIconImg = ImageIO.read(Objects.requireNonNull(this.getClass().getResourceAsStream("/icon.png")));
                } catch (Exception e) {
                    log.log(Level.WARNING, "unable to load tray icon", e);
                }
            }

            if (trayIconImg == null) {
                trayIconImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            }

            trayIcon = new TrayIcon(trayIconImg,
                    prefs != null && prefs.ShimejiEENameOverride != null && !prefs.ShimejiEENameOverride.isBlank()
                            ? prefs.ShimejiEENameOverride
                            : "ShimejiEE",
                    trayPopup);
            trayIcon.setImageAutoSize(true);

            // show tray icon
            SystemTray.getSystemTray().add(trayIcon);

        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to create tray menu", e);
            if (SystemTray.getSystemTray().getTrayIcons().length == 0) {
                System.exit(1);
            }
        }
    }


}
