package com.group_finity.mascot.imageset;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/**
 * Represents the standard directory layout for shimeji and shimejiEE.
 * Note that these paths can be named anything and not just the default names.
 *
 * @param confPath The folder that contains the default config files. Does not need to
 *                 exist if all the image sets have their own conf folders.
 *
 * @param imgPath The folder that contains the imageSet folders (or just the imageSet if isMonoImageSet is true).
 *                It also contains icon.png for the tray icon and optionally dock-icon.png for the dock icon.
 *                Needs to exist and contain specified files.
 *
 * @param soundPath Global sounds folder. Since this is just a place for the program can search for sound files,
 *                  It does not have to actually exist; but it can't be null either.
 *
 * @param isMonoImageSet Whether the imgPath folder directly contains an image set.
 *                       This was common in very old versions of shimeji.
 * */
public record ShimejiProgramFolder(
        Path confPath,
        Path imgPath,
        Path soundPath,
        boolean isMonoImageSet
) {
    private static final int MONO_CHECK_THRESHOLD = 20;

    private static final String DEFAULT_CONF_DIR = "conf";
    private static final String DEFAULT_IMG_DIR = "img";
    private static final String DEFAULT_SOUND_DIR = "sound";

    private static final String UNUSED_DIR = "unused";
    private static final String ICONS_DIR = "icons";
    private static final String ICON_NAME = "icon.png";
    private static final String SHIME_1 = "shime1.png";

    private static final String[] BEHAVIOR_FILENAMES = {
            "behavior.xml", "behaviors.xml",
            "行動.xml", "#U884c#U52d5.xml",
            "ÞíîÕïò.xml", "µ¦-.xml", "ìsô«.xml",
            "two.xml", "2.xml",
    };

    private static final String[] ACTIONS_FILENAMES = {
            "actions.xml", "action.xml",
            "動作.xml", "#U52d5#U4f5c.xml",
            "Õïòõ¢£.xml", "¦-º@.xml", "ô«ìý",
            "one.xml", "1.xml",
    };

    /**
     * Creates a {@link ShimejiProgramFolder} object from an existing shimeji installation.
     * It does not guarantee that any of the paths will actually exist.
     */
    public static ShimejiProgramFolder fromFolder(Path programFolder) {
        final Path imgPath = programFolder.resolve(DEFAULT_IMG_DIR);
        final Path confPath = programFolder.resolve(DEFAULT_CONF_DIR);
        final Path soundPath = programFolder.resolve(DEFAULT_SOUND_DIR);

        boolean isMono = false;

        // this isn't a surefire way to check if it's a mono imageSet, but it'll work most of the time
        try {
            isMono = listMatchingFilesIn(imgPath, (path, basicFileAttributes) -> {
                boolean isFile = basicFileAttributes.isRegularFile();
                boolean isImage = path.toString().toLowerCase().endsWith(".png");
                return isFile && isImage;
            }
            ).size() > MONO_CHECK_THRESHOLD;
        } catch (Exception ignored) {
            // we're not actually verifying that these exist, so this is fine
            // we just assume it's multi image set
        }

        return new ShimejiProgramFolder(confPath, imgPath, soundPath, isMono);
    }

    /**
     * Names of the image sets in the program folder
     * @return if {@link #isMonoImageSet()} is true then it returns a list with one empty string,
     *         otherwise it returns all directories in {@link #imgPath()} except dot-files, 'unused' and 'icons'
     */
    public List<String> getImageSetNames() throws IOException {
        if (isMonoImageSet) {
            return List.of("");
        }
        var matches =  listMatchingFilesIn(imgPath, ((path, basicFileAttributes) -> {
            String name = path.getFileName().toString().toLowerCase();
            boolean ignored = name.charAt(0) == '.' || name.equalsIgnoreCase(UNUSED_DIR) || name.equalsIgnoreCase(ICONS_DIR);
            boolean isDir = basicFileAttributes.isDirectory();
            return !ignored && isDir;
        }));
        return matches.stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    /**
     * @param imageSetName name of an imageSet in this program folder.
     */
    public Path getBehaviorConfPath(String imageSetName) throws FileNotFoundException {
        return getConfFilePath(imageSetName, BEHAVIOR_FILENAMES);
    }

    /**
     * @param imageSetName name of an imageSet in this program folder.
     */
    public Path getActionConfPath(String imageSetName) throws FileNotFoundException {
        return getConfFilePath(imageSetName, ACTIONS_FILENAMES);
    }

    /**
     * Path of the highest priority sound file found with given name and imageSet
     * @param imageSetName name of an imageSet in this program folder.
     * @param soundFileName name of the sound file to find.
     */
    public Path getSoundFilePath(String imageSetName, String soundFileName) throws FileNotFoundException {
        // this is a reversed order from the official code; but it fits better w other file finding in shimeji
        Path[] SOUND_DIRS = {
                imgPath.resolve(imageSetName).resolve(DEFAULT_SOUND_DIR),
                soundPath.resolve(imageSetName),
                soundPath
        };

        for (Path dir : SOUND_DIRS) {
            Path fp = Path.of(dir.toString(), soundFileName);
            if (Files.isRegularFile(fp)) {
                return fp;
            }
        }

        throw new FileNotFoundException();
    }

    /**
     * Path to tray icon
     */
    public Path getIconPath() {
        Path iconPath = imgPath.resolve(ICON_NAME);
        if (Files.isRegularFile(iconPath)) {
            return iconPath;
        } else {
            return null;
        }
    }

    /**
     * The available tray icons ({@code img/icons/*.png}), sorted by name.
     * <p>
     * A random one of these is used for the tray on startup.
     */
    public List<Path> getTrayIconChoices() {
        Path iconsDir = imgPath.resolve(ICONS_DIR);
        if (!Files.isDirectory(iconsDir)) {
            return List.of();
        }

        try {
            return listMatchingFilesIn(iconsDir, (path, attrs) ->
                    attrs.isRegularFile() && path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .stream().sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * path of an image representing the image set. (ie, the one used in the chooser)
     * @param imageSetName name of an imageSet in this program folder.
     */
    public Path getIconPathForImageSet(String imageSetName) {
        Path imageSetPath = imgPath.resolve(imageSetName);

        Path iconPath = imageSetPath.resolve(ICON_NAME);
        if (Files.isRegularFile(iconPath)) {
            return iconPath;
        }
        iconPath = imageSetPath.resolve(SHIME_1);
        if (Files.isRegularFile(iconPath)) {
            return iconPath;
        } else {
            return null;
        }
    }

    //--- info.xml

    /**
     * Fields of a standard shimeji info.xml file
     */
    public enum InfoField {
        NAME, PREVIEW_IMAGE, SPLASH_IMAGE,
        ARTIST_NAME, ARTIST_URL,
        SCRIPTER_NAME, SCRIPTER_URL,
        COMMISSIONER_NAME, COMMISSIONER_URL,
        SUPPORT_NAME, SUPPORT_URL;
    }

    /**
     * Reads the info/credits file ({@code info.xml}) of an image set.
     *
     * @param imageSetName name of an imageSet in this program folder.
     * @return the parsed values, or null when the image set has no readable info file.
     */
    public Map<InfoField, String> readInfoFile(String imageSetName) {
        Path[] INFO_DIRS = {
                imgPath.resolve(imageSetName).resolve(DEFAULT_CONF_DIR),
                confPath.resolve(imageSetName),
                confPath,
        };

        Path infoPath = null;
        for (Path dir : INFO_DIRS) {
            Path fp = dir.resolve("info.xml");
            if (Files.isRegularFile(fp)) {
                infoPath = fp;
                break;
            }
        }

        if (infoPath == null) {
            return null;
        }

        try (var in = Files.newInputStream(infoPath)) {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            var root = doc.getDocumentElement();
            if (root == null) {
                return null;
            }

            Map<InfoField, String> ret = new EnumMap<>(InfoField.class);
            var children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element el)) {
                    continue;
                }

                switch (el.getNodeName()) {
                    case "Name" -> putText(ret, InfoField.NAME, el);
                    case "PreviewImage" -> putText(ret, InfoField.PREVIEW_IMAGE, el);
                    case "SplashImage" -> putText(ret, InfoField.SPLASH_IMAGE, el);
                    case "Artist" -> putCred(ret, InfoField.ARTIST_NAME, InfoField.ARTIST_URL, el);
                    case "Scripter" -> putCred(ret, InfoField.SCRIPTER_NAME, InfoField.SCRIPTER_URL, el);
                    case "Commissioner" -> putCred(ret, InfoField.COMMISSIONER_NAME, InfoField.COMMISSIONER_URL, el);
                    case "Support" -> putCred(ret, InfoField.SUPPORT_NAME, InfoField.SUPPORT_URL, el);
                    default -> { }
                }
            }
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    private static void putText(Map<InfoField, String> out, InfoField field, Element el) {
        String text = el.getTextContent();
        if (text != null && !text.isBlank()) {
            out.put(field, text.trim());
        }
    }

    private static void putCred(Map<InfoField, String> out, InfoField nameField, InfoField urlField, Element el) {
        var name = el.getAttribute("Name");
        if (name != null && !name.isBlank()) {
            out.put(nameField, name.trim());
        }
        var url = el.getAttribute("URL");
        if (url != null && !url.isBlank()) {
            out.put(urlField, url.trim());
        }
    }

    private static List<Path> listMatchingFilesIn(Path folder, BiPredicate<Path, BasicFileAttributes> filter) throws IOException {
        try (Stream<Path> s = Files.find(folder,1, filter)) {
            return s.filter(path -> !path.equals(folder)).toList();
        }
    }

    private Path getConfFilePath(String imageSetName, String[] allowedNames) throws FileNotFoundException {
        Path[] CONF_DIRS = {
                imgPath.resolve(imageSetName).resolve(DEFAULT_CONF_DIR),
                confPath.resolve(imageSetName),
                confPath
        };

        for (Path dir : CONF_DIRS) {
            for (String name : allowedNames) {
                Path fp = dir.resolve(name);
                if (Files.isRegularFile(fp)) {
                    return fp;
                }
            }
        }

        throw new FileNotFoundException("Unable to locate config files for: " + imageSetName);
    }

}