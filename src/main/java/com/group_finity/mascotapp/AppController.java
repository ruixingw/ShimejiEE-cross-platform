package com.group_finity.mascotapp;

import com.group_finity.mascot.*;
import com.group_finity.mascot.config.XmlConfiguration;
import com.group_finity.mascot.config.DefaultPoseLoader;
import com.group_finity.mascot.exception.ConfigurationException;
import com.group_finity.mascot.image.ImagePairLoaderBuilder;
import com.group_finity.mascot.imageset.ImageSet;
import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascot.imageset.ShimejiImageSet;
import com.group_finity.mascot.manager.DefaultManager;
import com.group_finity.mascot.sound.SoundLoader;
import com.group_finity.mascot.ui.NativeUi;
import com.group_finity.mascot.window.contextmenu.MenuItemRep;
import com.group_finity.mascot.window.contextmenu.MenuRep;
import com.group_finity.mascot.window.contextmenu.TopLevelMenuRep;
import com.group_finity.mascot.imageset.ImageSetManager;
import com.group_finity.mascot.imageset.ImageSetSelectionDelegate;
import com.group_finity.mascot.environment.WindowTitleFilter;
import com.group_finity.mascotapp.prefs.ComplexPrefs;
import com.group_finity.mascotapp.prefs.MutablePrefs;
import com.group_finity.mascotapp.prefs.Prefs;
import com.group_finity.mascotnative.shared.swingui.AboutWindow;
import com.group_finity.mascotnative.shared.swingui.DebugWindow;
import com.group_finity.mascotnative.shared.swingui.InformationWindow;
import com.group_finity.mascotnative.shared.swingui.SettingsWindow;
import org.xml.sax.SAXException;


import javax.xml.parsers.ParserConfigurationException;
import java.awt.Point;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static java.util.Map.entry;

/**
 * The main app instance,
 * manages/responds to user actions such as changing settings
 */
public final class AppController implements Runnable, ImageSetSelectionDelegate, Controller {
    private static final Logger log = Logger.getLogger(AppController.class.getName());

    // Action that matches the followCursor action
    static final String BEHAVIOR_GATHER = "ChaseMouse";

    private static final String USER_BEHAVIORNAMES_FILE = "user-behaviornames.properties";

    private static final Path SETTINGS_PATH;

    static {
        System.setProperty("java.util.PropertyResourceBundle.encoding", "UTF-8");

        final var logPropsPath = Constants.JAR_DIR.resolve(Path.of("conf","logging.properties"));
        try (var ins = new FileInputStream(logPropsPath.toFile())) {
            LogManager.getLogManager().readConfiguration(ins);
        } catch (final SecurityException | IOException e) {
            e.printStackTrace();
        }

        String settingsPathProp = System.getProperty(Constants.PREF_PROP_PREFIX + "SettingsPath");
        SETTINGS_PATH = settingsPathProp != null
                ? Path.of(settingsPathProp)
                : Constants.JAR_DIR.resolve(Path.of("conf","settings.properties"));
    }

    //--------//
    private ShimejiProgramFolder programFolder = ShimejiProgramFolder.fromFolder(Constants.JAR_DIR);

    private Locale locale = Locale.ENGLISH;
    private MutablePrefs prefs = new MutablePrefs();

    /** image set selection parsed at startup, applied once native is ready */
    private Collection<String> pendingSelection = List.of();

    private final DefaultManager manager = new DefaultManager();
    private final ImageSetManager imageSets = new ImageSetManager(this::loadImageSet, this);
    private NativeUi ui;

    //-------------------------------------//
    @Override
    public void run() {
        try {
            if (!acquireSingleInstanceLock()) {
                // the ui isn't up yet, so use a bare dialog
                javax.swing.JOptionPane.showMessageDialog(null, Tr.tr("AlreadyRunning"),
                        "ShimejiEE", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }

            // init settings first so the Environment pref can pick the backend
            loadAllSettings(SETTINGS_PATH);
            Tr.loadLanguage(locale);
            Tr.setCustomBehaviorTranslations(Prefs.readProps(Constants.JAR_DIR.resolve(Path.of("conf", USER_BEHAVIORNAMES_FILE))));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> writeAllSettings(SETTINGS_PATH)));

            // init native (needs to be before the image sets are loaded)
            final String nativeProp = System.getProperty(
                    "com.group_finity.mascotnative",
                    prefs.Environment == null || prefs.Environment.isBlank() ? Constants.NATIVE_PKG_DEFAULT : prefs.Environment.trim());
            NativeFactory.init(nativeProp, Constants.NATIVE_LIB_DIR);
            NativeFactory.getInstance().getEnvironment().init();
            ui = NativeFactory.getInstance().createUi(this);

            // image sets need the native backend, so load them last
            imageSets.setSelected(pendingSelection);

            applyInteractiveWindowFilter();
            applyVirtualDesktopSettings();
            checkAccessibilityPermission();

            manager.start();

            ui.start(prefs, () -> {
                // get selections (show chooser if needed)
                if (imageSets.getSelected().isEmpty() || prefs.AlwaysShowShimejiChooser) {
                    ui.requestImageSetChooser(imageSets.getSelected(), programFolder);
                }
            });

        } catch (Exception | Error error) {
            error.printStackTrace();
            log.log(Level.SEVERE, error.getMessage(), error);
            if (ui != null) {
                ui.showError(error.getMessage());
            }
            System.exit(0);
        }
    }

    //--------single instance--------//

    private static FileChannel instanceLockChannel;
    private static FileLock instanceLock;

    /**
     * Prevents two shimeji instances from fighting over the same window.
     *
     * @return false when another instance is already running.
     */
    private static boolean acquireSingleInstanceLock() {
        try {
            var lockFile = Constants.JAR_DIR.resolve(".shimeji-instance.lock").toFile();
            instanceLockChannel = new RandomAccessFile(lockFile, "rw").getChannel();
            instanceLock = instanceLockChannel.tryLock();
            return instanceLock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }

    //--------permissions--------//

    /**
     * macOS accessibility is needed for ie (window) interactions.
     * <p>
     * Offers the system permission prompt once when it's missing and the user
     * has window interactions enabled.
     */
    private void checkAccessibilityPermission() {
        try {
            var env = NativeFactory.getInstance().getEnvironment();
            if (prefs.Throwing && !env.isAccessibilityTrusted()) {
                if (ui.askYesNo(Tr.tr("AxPermissionTitle"), Tr.tr("AxPermissionMessage"))) {
                    env.requestAccessibilityTrust();
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "accessibility check failed", e);
        }
    }

    /**
     * Pushes the interactive window captions from the prefs to the environment.
     */
    private void applyInteractiveWindowFilter() {
        NativeFactory.getInstance().getEnvironment().setInteractiveWindowFilter(
                WindowTitleFilter.parse(prefs.InteractiveWindows),
                WindowTitleFilter.parse(prefs.InteractiveWindowsBlacklist));
    }

    /**
     * Pushes the window mode (virtual desktop) settings to the environment.
     */
    private void applyVirtualDesktopSettings() {
        NativeFactory.getInstance().getEnvironment().applyVirtualDesktopSettings(
                prefs.WindowSize, prefs.Background, prefs.BackgroundMode, prefs.BackgroundImage);
    }

    /**
     * Switches the backend at runtime when the Environment pref asks for
     * something other than the current one.
     */
    private void applyEnvironment() {
        var target = prefs.Environment == null ? "" : prefs.Environment.trim();
        if (target.equals("auto") || target.isEmpty()) {
            target = Constants.NATIVE_PKG_DEFAULT;
        }
        if (!target.equals(NativeFactory.getCurrentSubpkg())) {
            switchEnvironment(target);
        }
    }

    private void switchEnvironment(String subpkg) {
        manager.disposeAll();
        NativeFactory.init(subpkg, Constants.NATIVE_LIB_DIR);
        NativeFactory.getInstance().getEnvironment().init();
        applyInteractiveWindowFilter();
        applyVirtualDesktopSettings();
        reloadImageSets();
    }

    //--------information splash--------//

    /**
     * Shows the credits splash the first time an image set is used
     * (or every time with AlwaysShowInformationScreen).
     */
    private void showInformationScreen(String name) {
        if (!prefs.AlwaysShowInformationScreen
                && WindowTitleFilter.parse(prefs.InformationDismissed).contains(name)) {
            return;
        }

        var info = programFolder.readInfoFile(name);
        if (info == null || info.get(com.group_finity.mascot.imageset.ShimejiProgramFolder.InfoField.SPLASH_IMAGE) == null) {
            return;
        }

        InformationWindow.show(programFolder, name, info, () -> {
            var dismissed = new ArrayList<>(WindowTitleFilter.parse(prefs.InformationDismissed));
            if (!dismissed.contains(name)) {
                dismissed.add(name);
                prefs.InformationDismissed = String.join("/", dismissed);
            }
        });
    }

    //--------imageSet management---------//

    @Override public void imageSetHasBeenAdded(String name, ImageSet imageSet) {
        createMascot(name);
        showInformationScreen(name);
    }
    @Override public void dependencyHasBecomeSelection(String name, ImageSet imageSet) { createMascot(name); }

    @Override
    public void imageSetWillBeRemoved(String name, ImageSet imageSet) {
        manager.disposeIf(m -> m.getImageSet().equals(name));
    }

    @Override
    public void imageSetHasBeenRemoved(String name, ImageSet imageSet) {
        try {
            imageSet.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads resources for the specified image set.
     */
    private ImageSet loadImageSet(final String imageSet) {
        var imsProps = prefs;

        if (!prefs.IgnoreImagesetProperties) {
            try {
                var imgSetPropsPath = programFolder.imgPath()
                        .resolve(imageSet)
                        .resolve("conf/imageset.properties");

                imsProps = Prefs.deserializeMutable(Prefs.readProps(imgSetPropsPath), imsProps);
            } catch (Exception e) {
                log.log(Level.WARNING, "Unable to load image set props", e);
            }
        }

        try {
            return loadImageSetFrom(programFolder, imageSet, imsProps);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to load image set", e);
            ui.showError(e.getMessage());
        }
        return  null;
    }

    private static ImageSet loadImageSetFrom(ShimejiProgramFolder pf, String name, MutablePrefs prefs) throws ParserConfigurationException, IOException, SAXException, ConfigurationException {
        double scale = 1.0;
        try {
            scale = prefs.Scaling;
            scale = scale > 0.0 ? scale : 1.0;
        } catch (Exception ignored) {}

        var imgLoader = new ImagePairLoaderBuilder()
                .setScaling(scale)
                .setOpacity(prefs.Opacity > 0.0 && prefs.Opacity <= 1.0 ? prefs.Opacity : 1.0)
                .setLogicalAnchors(prefs.LogicalAnchors)
                .setAsymmetryNameScheme(prefs.AsymmetryNameScheme)
                .setPixelArtScaling(prefs.PixelArtScaling)
                .setHqx(prefs.HqxScaling)
                .buildForBasePath(pf.imgPath().resolve(name));

        SoundLoader soundLoader = new SoundLoader(pf, name);
        soundLoader.setFixRelativeGlobalSound(prefs.FixRelativeGlobalSound);

        Path actionsPath = pf.getActionConfPath(name);
        Path behaviorPath = pf.getBehaviorConfPath(name);

        var poseLoader = new DefaultPoseLoader(imgLoader, soundLoader);
        var conf = XmlConfiguration.loadUsing(poseLoader, actionsPath, behaviorPath);

        return new ShimejiImageSet(conf, imgLoader, soundLoader);
    }

    //----- Ui callbacks

    @Override
    public void setLocale(Locale newLocale) {
        if (!locale.equals(newLocale)) {
            locale = newLocale;
            Tr.loadLanguage(newLocale);
            //reload tray icon if it exists
            ui.reload();
        }
    }

    @Override
    public void setImageSets(Collection<String> selection) {
        imageSets.setSelected(selection);
    }

    @Override
    public void reloadImageSets() {
        var selection = imageSets.getSelected();
        imageSets.setSelected(List.of());
        imageSets.setSelected(selection);
    }

    @Override
    public void runGlobalAction(String name) {
        // need to put this on a queue later
        if (mainMenuActions.containsKey(name)){
            mainMenuActions.get(name).run();
        } else {
            System.err.println("Invalid action: " + name);
        }
    }

    //----------mascot creation-----------//

    /**
     * Spawns a random mascot
     */
    private void createMascot() {
        var ims = imageSets.getRandomSelection();
        if (ims == null) {
            ui.requestImageSetChooser(imageSets.getSelected(), programFolder);
        } else {
            createMascot(ims);
        }
    }

    /**
     * Creates a mascot from the specified imageSet.
     * <p>
     * Fails if the image set has not been loaded.
     */
    private void createMascot(String imageSet) {
        // Create one mascot
        final Mascot mascot = new Mascot(imageSet, prefs, imageSets, new MascotUiFactory() {
            @Override
            public DebugUi createDebugUiFor(Mascot mascot) {
                return new DebugWindow();
            }

            @Override
            public TopLevelMenuRep createContextMenuFor(Mascot mascot) {
                return createCtxMenuFor(mascot);
            }
        });

        // Create it outside the bounds of the screen
        mascot.setAnchor(new Point(-4000, -4000));

        // Randomize the initial orientation
        mascot.setLookRight(Math.random() < 0.5);

        try {
            mascot.setBehavior(mascot.getOwnImageSet().getConfiguration().buildBehavior(null, mascot));
            manager.add(mascot);
        } catch (Exception e) {
            log.log(Level.SEVERE, imageSet + " fatal error, can not be started.", e);
            ui.showError(
                    Tr.tr("CouldNotCreateShimejiErrorMessage") + ": " + imageSet +
                    ".\n" + e.getMessage()
                    + "\n" + Tr.tr("SeeLogForDetails"));
            mascot.dispose();
        }
    }

///=======v This class ends here, everything below is meant to be easily deletable v========//

    //---------Setting storage/extraction------------//

    private void loadAllSettings(Path inputFilePath) {
        var props = Prefs.readProps(inputFilePath);

        this.prefs = Prefs.deserializeMutable(props);
        var complexPrefs = Prefs.deserializeComplex(props);

        var localeProp = complexPrefs.Language;
        if (localeProp != null) {
            locale = Locale.forLanguageTag(localeProp);
        }

        programFolder = complexPrefs.getProgramFolder(programFolder);
        pendingSelection = complexPrefs.getValidActiveImageSets(programFolder);
    }

    private void writeAllSettings(Path outputFilePath) {
        Map<String, String> props = Prefs.serializeMutable(prefs);

        // complex
        // program folder excluded on purpose since there's not going to be a gui for it
        if (!locale.equals(Locale.getDefault())) {
            props.put("Language", locale.toLanguageTag());
        }

        var ims = ComplexPrefs.serializeActiveImageSets(imageSets.getSelected());
        props.put("ActiveShimeji", ims);

        Prefs.writeProps(props, outputFilePath);
    }

    private final Map<String, Runnable> mainMenuActions = Map.ofEntries(
            entry("CallShimeji", this::createMascot),
            entry("FollowCursor", () -> manager.trySetBehaviorAll(BEHAVIOR_GATHER)),
            entry("ReduceToOne", manager::reduceToOne),
            entry("RestoreWindows", () -> NativeFactory.getInstance().getEnvironment().restoreIE()),
            entry("ChooseShimeji", () -> ui.requestImageSetChooser(imageSets.getSelected(), programFolder)),
            entry("ReloadMascots", this::reloadImageSets),
            entry("Settings", () -> SettingsWindow.show(prefs, programFolder, imageSets.getSelected(), () -> {
                applyInteractiveWindowFilter();
                applyVirtualDesktopSettings();
                applyEnvironment();
                reloadImageSets();
            })),
            entry("PauseAnimations", () -> manager.setAllAnimating(false)),
            entry("ResumeAnimations", () -> manager.setAllAnimating(true)),
            entry("About", () -> AboutWindow.show(programFolder, imageSets.getSelected())),
            entry("DismissAll", manager::disposeAll),
            entry("Quit", () -> System.exit(0))
    );

    private final Map<String, Consumer<Mascot>> mascotActions = Map.ofEntries(
            entry("CallAnother", m -> createMascot(m.getImageSet())),
            entry("RevealStatistics", Mascot::startDebugUi),
            entry("FollowCursor", m -> {
                try {
                    var conf = m.getOwnImageSet().getConfiguration();
                    m.setBehavior(conf.buildBehavior(conf.getSchema().getString(BEHAVIOR_GATHER)));
                } catch (Exception ignored) {
                    // again, we're ignoring ChaseMouse not existing
                }
            }),
            entry("PauseAnimations", m -> m.setAnimating(false)),
            entry("ResumeAnimations", m -> m.setAnimating(true)),
            entry("Dismiss", Mascot::dispose),
            entry("DismissOthers", m -> manager.disposeIf(mascot -> mascot.id != m.id && mascot.getImageSet().equals(m.getImageSet()))),
            entry("DismissAllOthers", m -> manager.disposeIf(mascot -> mascot.id != m.id))
    );

    //----------Menus------------//

    //---Console Menu

    private MenuItemRep repActionBtn(String title, String action, Mascot m) {
        if (mascotActions.containsKey(action)) {
            return new MenuItemRep(title, () -> mascotActions.get(action).accept(m));
        }
        if (mainMenuActions.containsKey(action)) {
            return new MenuItemRep(title, mainMenuActions.get(action));
        }
        return new MenuItemRep(title, null, false);
    }

    private List<MenuItemRep> createBehaviourMenuItemsFor(Mascot m) {
        var conf = m.getOwnImageSet().getConfiguration();

        List<MenuItemRep> bvItems = new ArrayList<>();

        for (String bvName : conf.getBehaviorNames()) {
            String title = prefs.TranslateBehaviorNames ? Tr.trBv(bvName) : bvName;
            try {
                var bv = conf.buildBehavior(bvName);
                if (bv.isHidden()) {
                    continue;
                }
                bvItems.add(new MenuItemRep(title, () -> {
                    try {
                        m.setBehavior(conf.buildBehavior(bvName));
                    } catch (Exception err) {
                        ui.showError(Tr.tr("CouldNotSetBehaviourErrorMessage") + "\n" + err.getMessage());
                    }
                }));
            } catch (Exception e) {
                bvItems.add(new MenuItemRep(title, null, false));
                log.log(Level.WARNING, "Failed to create Behaviour menu button for: " + bvName, e);
            }
        }

        return bvItems;
    }

    private List<MenuItemRep> createBehaviourToggleItemsFor(Mascot m) {
        var conf = m.getOwnImageSet().getConfiguration();

        List<MenuItemRep> items = new ArrayList<>();

        for (String bvName : conf.getBehaviorNames()) {
            if (!conf.isBehaviorToggleable(bvName)) {
                continue;
            }
            String title = prefs.TranslateBehaviorNames ? Tr.trBv(bvName) : bvName;
            boolean enabled = m.isBehaviorEnabled(bvName);
            items.add(new MenuItemRep(title, () -> setMascotBehaviorEnabled(m, bvName, !m.isBehaviorEnabled(bvName)), true, enabled));
        }

        if (items.isEmpty()) {
            items.add(new MenuItemRep(Tr.tr("More"), null, false));
        }

        return items;
    }

    /**
     * Persists the per image set behavior toggles (DisabledBehaviours.<set>).
     */
    private void setMascotBehaviorEnabled(Mascot m, String behaviorName, boolean enabled) {
        var setName = m.getImageSet();
        var current = new ArrayList<>(WindowTitleFilter.parse(prefs.DisabledBehaviours.getOrDefault(setName, "")));
        current.remove(behaviorName);
        if (!enabled) {
            current.add(behaviorName);
        }
        prefs.DisabledBehaviours.put(setName, String.join("/", current));
    }

    private TopLevelMenuRep createCtxMenuFor(Mascot m) {
        var rep = new TopLevelMenuRep("mascot",
                repActionBtn(Tr.tr("CallAnother"), "CallAnother", m),
                repActionBtn(Tr.tr("FollowCursor"), "FollowCursor", m),
                repActionBtn(Tr.tr("RestoreWindows"), "RestoreWindows", m),
                repActionBtn(Tr.tr("RevealStatistics"), "RevealStatistics", m),
                MenuItemRep.SEPARATOR,
                new MenuRep(Tr.tr("SetBehaviour"), createBehaviourMenuItemsFor(m).toArray(new MenuItemRep[0])),
                new MenuRep(Tr.tr("AllowedBehaviours"), createBehaviourToggleItemsFor(m).toArray(new MenuItemRep[0])),
                MenuItemRep.SEPARATOR,
                repActionBtn(Tr.tr("PauseAnimations"), "PauseAnimations", m),
                repActionBtn(Tr.tr("ResumeAnimations"), "ResumeAnimations", m),
                MenuItemRep.SEPARATOR,
                repActionBtn(Tr.tr("Dismiss"), "Dismiss", m),
                repActionBtn(Tr.tr("DismissOthers"), "DismissOthers", m),
                repActionBtn(Tr.tr("DismissAllOthers"), "DismissAllOthers", m),
                repActionBtn(Tr.tr("DismissAll"), "DismissAll", m)
        );

        rep.setOnOpenAction(() -> m.setAnimating(false));
        rep.setOnCloseAction(() -> m.setAnimating(true));

        return rep;
    }

}
