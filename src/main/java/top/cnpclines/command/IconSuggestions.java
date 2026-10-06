package top.cnpclines.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.commands.CommandSourceStack;

public final class IconSuggestions {
    private static final String RESOURCE_PREFIX = "assets/cnpclines/textures/overlay/message/icons/";
    private static volatile List<String> cachedIcons;

    public static final SuggestionProvider<CommandSourceStack> PROVIDER = IconSuggestions::suggest;

    private IconSuggestions() {
    }

    private static CompletableFuture<Suggestions> suggest(
        CommandContext<CommandSourceStack> context, SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (String path : icons()) {
            if (path.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    }

    private static List<String> icons() {
        List<String> icons = cachedIcons;
        if (icons == null) {
            icons = scanIcons();
            cachedIcons = icons;
        }
        return icons;
    }

    private static List<String> scanIcons() {
        List<String> result = new ArrayList<>();
        try {
            URL location = IconSuggestions.class.getProtectionDomain().getCodeSource().getLocation();
            Path self = Path.of(location.toURI());
            if (Files.isDirectory(self)) {
                Path iconsRoot = self.resolve(RESOURCE_PREFIX);
                if (Files.isDirectory(iconsRoot)) {
                    try (Stream<Path> stream = Files.walk(iconsRoot)) {
                        stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .map(path -> iconsRoot.relativize(path).toString().replace('\\', '/'))
                            .forEach(result::add);
                    }
                }
            } else {
                try (ZipFile zip = new ZipFile(self.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.isDirectory()
                            && entry.getName().startsWith(RESOURCE_PREFIX)
                            && entry.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                            result.add(entry.getName().substring(RESOURCE_PREFIX.length()));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException ignored) {
        }
        Collections.sort(result);
        return result;
    }
}
