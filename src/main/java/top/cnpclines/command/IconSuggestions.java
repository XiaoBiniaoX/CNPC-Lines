package top.cnpclines.command;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class IconSuggestions {
    private static final String RESOURCE_PREFIX = "assets/cnpclines/textures/overlay/message/icons/";
    private static final Logger LOGGER = LogManager.getLogger("cnpclines");
    private static volatile List<String> cachedIcons;

    private IconSuggestions() {
    }

    public static List<String> icons() {
        List<String> icons = cachedIcons;
        if (icons == null || icons.isEmpty()) {
            icons = scanIcons();
            if (icons.isEmpty()) {
                LOGGER.warn("IconSuggestions found no icon files; icon tab completion stays empty");
            } else {
                if (cachedIcons == null) {
                    LOGGER.info("IconSuggestions discovered {} icons", icons.size());
                }
                cachedIcons = icons;
            }
        }
        return icons;
    }

    private static List<String> scanIcons() {
        List<String> result = new ArrayList<>();
        try {
            scanModSources(result);
        } catch (Throwable t) {
            LOGGER.warn("IconSuggestions mod source scan failed", t);
        }
        if (result.isEmpty()) {
            try {
                scanCodeSource(result);
            } catch (Throwable t) {
                LOGGER.warn("IconSuggestions code source scan failed", t);
            }
        }
        Collections.sort(result);
        return result;
    }

    private static void scanModSources(List<String> result) throws IOException {
        for (ModContainer container : Loader.instance().getIndexedModList().values()) {
            File source = container.getSource();
            if (source == null || !source.exists()) {
                continue;
            }
            if (source.isDirectory()) {
                Path root = source.toPath().resolve(RESOURCE_PREFIX);
                if (Files.isDirectory(root)) {
                    collectDirectory(root, result);
                }
            } else {
                collectZip(source, result);
            }
        }
    }

    private static void scanCodeSource(List<String> result) throws Exception {
        URL location = IconSuggestions.class.getProtectionDomain().getCodeSource() == null
            ? null
            : IconSuggestions.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            return;
        }
        Path self;
        try {
            self = Paths.get(location.toURI());
        } catch (URISyntaxException e) {
            self = Paths.get(location.getPath());
        }
        if (Files.isDirectory(self)) {
            Path root = self.resolve(RESOURCE_PREFIX);
            if (Files.isDirectory(root)) {
                collectDirectory(root, result);
            }
        } else if (Files.isRegularFile(self)) {
            collectZip(self.toFile(), result);
        }
    }

    private static void collectDirectory(Path root, List<String> result) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .forEach(result::add);
        }
    }

    private static void collectZip(File file, List<String> result) {
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()
                    && entry.getName().startsWith(RESOURCE_PREFIX)
                    && entry.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    result.add(entry.getName().substring(RESOURCE_PREFIX.length()));
                }
            }
        } catch (ZipException ignored) {
        } catch (IOException ignored) {
        }
    }
}
