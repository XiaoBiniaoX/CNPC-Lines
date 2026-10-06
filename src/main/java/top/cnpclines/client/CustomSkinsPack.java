package top.cnpclines.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CustomSkinsPack {

    public static final String PACK_NAME = "Custom Skins";
    public static final String BUILTIN_PACK_NAME = "CNPC Lines Built-in";
    public static final String NAMESPACE = "customnpcs";
    public static final String DESCRIPTION =
        "由CNPCLines自动创建的材质包，用于便捷添加皮肤、披风、额外材质文件。";
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private static final Logger LOGGER = LogManager.getLogger("cnpclines");
    private static final String[] BASE_DIRS = {
        "textures/entity/humanmale",
        "textures/cloak",
        "textures/overlays",
    };

    private static boolean attempted;

    private CustomSkinsPack() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        if (attempted) {
            try {
                if (top.cnpclines.client.gui.ResourceIndex.consumePendingReload()) {
                    minecraft.refreshResources();
                    top.cnpclines.client.gui.ResourceIndex.refresh();
                }
            } catch (Exception e) {
                LOGGER.warn("Deferred resource reload failed", e);
            }
            return;
        }
        attempted = true;
        try {
            ensure(minecraft);
            top.cnpclines.client.gui.ResourceIndex.ensureFresh();
        } catch (Exception e) {
            LOGGER.warn("Custom Skins pack bootstrap failed", e);
        }
    }

    private static void ensure(Minecraft minecraft) throws IOException {
        writePackMeta(root(), DESCRIPTION);
        boolean deselected = cleanupLegacyBuiltin(minecraft);
        for (String dir : BASE_DIRS) {
            Files.createDirectories(mirror(NAMESPACE, dir));
        }
        boolean filesChanged = sanitizeExisting(root());
        top.cnpclines.client.gui.ResourceIndex.ensureFresh();
        ResourcePackRepository repository = minecraft.getResourcePackRepository();
        repository.updateRepositoryEntriesAll();
        boolean selectionChanged = addSelectedPack(minecraft, PACK_NAME);
        if (deselected) {
            selectionChanged = true;
        }
        boolean pendingReload = top.cnpclines.client.gui.ResourceIndex.consumePendingReload();
        if (selectionChanged || filesChanged || pendingReload) {
            minecraft.refreshResources();
        }
    }

    private static boolean cleanupLegacyBuiltin(Minecraft minecraft) {
        try {
            boolean selected = false;
            for (String id : minecraft.gameSettings.resourcePacks) {
                if (id.endsWith(BUILTIN_PACK_NAME)) {
                    selected = true;
                    break;
                }
            }
            Path legacy = builtinRoot();
            boolean existed = Files.exists(legacy);
            if (existed) {
                deleteRecursively(legacy);
                LOGGER.info("Removed legacy builtin resource pack: {}", legacy);
            }
            if (selected) {
                minecraft.gameSettings.resourcePacks.removeIf(id -> id.endsWith(BUILTIN_PACK_NAME));
                minecraft.gameSettings.saveOptions();
            }
            return selected;
        } catch (Exception e) {
            LOGGER.warn("Legacy builtin pack cleanup failed", e);
            return false;
        }
    }

    private static void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.delete(item);
                } catch (IOException ignored) {
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to delete {}", path, e);
        }
    }

    private static void writePackMeta(Path root, String description) throws IOException {
        Path meta = root.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return;
        }
        Files.createDirectories(root);
        String json = "{\"pack\":{\"pack_format\":3,\"description\":\"" + description + "\"}}";
        Files.write(meta, json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean addSelectedPack(Minecraft minecraft, String packName) {
        String id = findPackId(minecraft, packName);
        if (id == null) {
            LOGGER.warn("Pack {} was not discovered by the repository", packName);
            return false;
        }
        if (minecraft.gameSettings.resourcePacks.contains(id)) {
            return false;
        }
        minecraft.gameSettings.resourcePacks.add(id);
        minecraft.gameSettings.saveOptions();
        return true;
    }

    private static String findPackId(Minecraft minecraft, String packName) {
        for (ResourcePackRepository.Entry entry : minecraft.getResourcePackRepository().getRepositoryEntriesAll()) {
            String id = entry.getResourcePackName();
            if (id.equals(packName) || id.endsWith("/" + packName) || id.endsWith(packName)) {
                return id;
            }
        }
        return null;
    }

    private static boolean sanitizeExisting(Path root) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return false;
        }
        boolean changed = false;
        try (Stream<Path> walk = Files.walk(assets)) {
            List<Path> pngs = walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .collect(Collectors.toList());
            for (Path path : pngs) {
                String name = path.getFileName().toString();
                String fixed = sanitizeFileName(name);
                if (fixed.equals(name)) {
                    continue;
                }
                Path target = Files.exists(path.resolveSibling(fixed))
                    ? unique(path.getParent(), fixed)
                    : path.resolveSibling(fixed);
                Files.move(path, target);
                changed = true;
                LOGGER.info("Renamed invalid skin file {} -> {}", name, target.getFileName());
            }
        } catch (Exception e) {
            LOGGER.warn("Sanitize existing pack files failed", e);
        }
        return changed;
    }

    public static Path root() {
        return Minecraft.getMinecraft().getResourcePackRepository().getDirResourcepacks().toPath().resolve(PACK_NAME);
    }

    public static Path builtinRoot() {
        return Minecraft.getMinecraft().getResourcePackRepository().getDirResourcepacks().toPath().resolve(BUILTIN_PACK_NAME);
    }

    public static Path assets() {
        return root().resolve("assets");
    }

    public static Path mirror(String namespace, String dir) {
        return under(root(), namespace, dir);
    }

    private static Path under(Path root, String namespace, String dir) {
        Path path = root.resolve("assets").resolve(namespace);
        if (dir != null) {
            for (String part : dir.split("/")) {
                if (!part.isEmpty()) {
                    path = path.resolve(part);
                }
            }
        }
        return path;
    }

    public static boolean owns(String namespace, String dir) {
        return Files.isDirectory(mirror(namespace, dir));
    }

    public static boolean ownsFile(String namespace, String path) {
        return Files.isRegularFile(mirror(namespace, path));
    }

    public static final class ImportResult {
        private final int added;
        private final int skipped;
        private final List<String> names;

        public ImportResult(int added, int skipped, List<String> names) {
            this.added = added;
            this.skipped = skipped;
            this.names = names;
        }

        public int added() {
            return added;
        }

        public int skipped() {
            return skipped;
        }

        public List<String> names() {
            return names;
        }
    }

    public static ImportResult importPng(String dir, List<Path> files) throws IOException {
        Path target = mirror(NAMESPACE, dir);
        Files.createDirectories(target);
        int added = 0;
        int skipped = 0;
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (Path file : files) {
                if (file == null || !Files.isRegularFile(file)) {
                    skipped++;
                    continue;
                }
                String raw = file.getFileName().toString();
                if (!raw.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    skipped++;
                    continue;
                }
                String name = sanitizeFileName(raw);
                Path destination = target.resolve(name);
                if (Files.exists(destination)) {
                    destination = unique(target, name);
                }
                try {
                    Files.copy(file, destination);
                } catch (IOException e) {
                    skipped++;
                    continue;
                }
                added++;
                names.add(destination.getFileName().toString());
            }
        }
        return new ImportResult(added, skipped, names);
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null) {
            return "skin.png";
        }
        String name = raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1).trim();
        }
        name = name.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_");
        boolean png = name.toLowerCase(Locale.ROOT).endsWith(".png");
        String stem = png ? name.substring(0, name.length() - 4) : name;
        stem = stem.replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (stem.isEmpty()) {
            return "skin.png";
        }
        return png ? stem + ".png" : stem;
    }

    public static String sanitizeFolderName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    private static Path unique(Path dir, String name) {
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 1; i < 1000; i++) {
            Path candidate = dir.resolve(stem + "_" + i + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return dir.resolve(stem + "_" + System.currentTimeMillis() + ext);
    }

    public static boolean createFolder(String namespace, String parent, String name) throws IOException {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return false;
        }
        Path path = mirror(namespace, parent).resolve(name);
        if (Files.exists(path)) {
            return false;
        }
        Files.createDirectories(path);
        return true;
    }

    public static String renameFolder(String namespace, String dir, String name) throws IOException {
        if (!NAME_PATTERN.matcher(name).matches()) {
            return "名称只能包含小写字母、数字、下划线";
        }
        Path source = mirror(namespace, dir);
        if (!Files.isDirectory(source)) {
            return "该文件夹来自其他资源包，无法重命名";
        }
        int lastSlash = dir.lastIndexOf('/', dir.length() - 2);
        String newName = dir.substring(0, lastSlash + 1) + name + "/";
        Path target = mirror(namespace, newName);
        if (Files.exists(target)) {
            return "同名文件夹已存在";
        }
        Files.move(source, target);
        return null;
    }

    public static String deleteFolder(String namespace, String dir) throws IOException {
        Path source = mirror(namespace, dir);
        if (!Files.isDirectory(source)) {
            return "该文件夹来自其他资源包，无法删除";
        }
        Files.walk(source)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                }
            });
        return null;
    }

    public static String deleteFile(String namespace, String path) throws IOException {
        Path source = mirror(namespace, path);
        if (!Files.isRegularFile(source)) {
            return "该文件来自其他资源包，无法删除";
        }
        Files.delete(source);
        return null;
    }
}
