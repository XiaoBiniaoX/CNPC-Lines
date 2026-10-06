package top.cnpclines.client.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import top.cnpclines.client.CustomSkinsPack;

public final class ResourceIndex {

    private static final Map<String, TreeMap<String, ResourceLocation>> FILES = new HashMap<>();
    private static final Map<String, Set<String>> DIRS = new HashMap<>();
    private static final Map<String, Weird> WEIRD = new HashMap<>();
    private static final Map<String, String> WEIRD_LOWER = new HashMap<>();
    private static final Map<String, String> WEIRD_STRIPPED = new HashMap<>();
    private static final Set<String> MATERIALIZED = new HashSet<>();
    private static final Pattern ALIAS_PATTERN = Pattern.compile("_u[0-9a-f]{2}_");
    private static final String HEX = "0123456789abcdef";
    private static boolean loaded;
    private static int packCount = -1;
    private static boolean pendingReload;

    private static final class Weird {
        private final String originalPath;
        private final byte[] bytes;
        private final boolean fromDisk;

        private Weird(String originalPath, byte[] bytes, boolean fromDisk) {
            this.originalPath = originalPath;
            this.bytes = bytes;
            this.fromDisk = fromDisk;
        }

        String originalPath() {
            return originalPath;
        }

        byte[] bytes() {
            return bytes;
        }

        boolean fromDisk() {
            return fromDisk;
        }
    }

    private ResourceIndex() {
    }

    public static void ensureFresh() {
        int count;
        try {
            count = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntriesAll().size();
        } catch (Exception e) {
            count = -1;
        }
        if (!loaded || count != packCount) {
            refresh();
        }
    }

    public static void refresh() {
        FILES.clear();
        DIRS.clear();
        WEIRD.clear();
        WEIRD_LOWER.clear();
        WEIRD_STRIPPED.clear();
        MATERIALIZED.clear();
        int count;
        try {
            count = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntriesAll().size();
        } catch (Exception e) {
            count = -1;
        }
        Set<Path> roots = collectRoots();
        for (Path root : roots) {
            scanRoot(root, isCustomPackRoot(root));
        }
        packCount = count;
        loaded = true;
        if (materializeWeird()) {
            pendingReload = true;
        }
        mergePackDisk();
        registerWeird();
    }

    public static boolean consumePendingReload() {
        boolean pending = pendingReload;
        pendingReload = false;
        return pending;
    }

    private static Set<Path> collectRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        try {
            File dir = Minecraft.getMinecraft().getResourcePackRepository().getDirResourcepacks();
            File[] files = dir == null ? null : dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    roots.add(file.getAbsoluteFile().toPath().normalize());
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (ModContainer container : Loader.instance().getIndexedModList().values()) {
                File source = container.getSource();
                if (source != null) {
                    roots.add(source.getAbsoluteFile().toPath().normalize());
                }
            }
        } catch (Exception ignored) {
        }
        addCodeSource(roots, Minecraft.class);
        addCodeSource(roots, ResourceIndex.class);
        addCodeSource(roots, Loader.class);
        return roots;
    }

    private static void addCodeSource(Set<Path> roots, Class<?> clazz) {
        try {
            CodeSource source = clazz.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return;
            }
            roots.add(Paths.get(source.getLocation().toURI()).toAbsolutePath().normalize());
        } catch (Exception ignored) {
        }
    }

    private static boolean isCustomPackRoot(Path root) {
        try {
            Path custom = CustomSkinsPack.root().toAbsolutePath().normalize();
            return root.equals(custom);
        } catch (Exception e) {
            return false;
        }
    }

    private static void scanRoot(Path root, boolean fromDisk) {
        if (Files.isDirectory(root)) {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(assets)) {
                walk.forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            addDiskDir(assets, path);
                            return;
                        }
                        String name = path.getFileName().toString();
                        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                            return;
                        }
                        String rel = assets.relativize(path).toString().replace('\\', '/');
                        int slash = rel.indexOf('/');
                        if (slash <= 0 || slash >= rel.length() - 1) {
                            return;
                        }
                        dispatch(rel.substring(0, slash), rel.substring(slash + 1), path, null, null, fromDisk);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        } else if (Files.isRegularFile(root)) {
            try (ZipFile zip = new ZipFile(root.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith("assets/")
                        || !name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                        continue;
                    }
                    String rel = name.substring("assets/".length());
                    int slash = rel.indexOf('/');
                    if (slash <= 0 || slash >= rel.length() - 1) {
                        continue;
                    }
                    dispatch(rel.substring(0, slash), rel.substring(slash + 1), null, zip, entry, fromDisk);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void addDiskDir(Path assets, Path dir) {
        String rel = assets.relativize(dir).toString().replace('\\', '/');
        int slash = rel.indexOf('/');
        if (slash <= 0) {
            return;
        }
        String ns = rel.substring(0, slash);
        String sub = rel.substring(slash + 1);
        if (sub.isEmpty() || !isValidNamespace(ns) || !isValidPath(sub)) {
            return;
        }
        if (sub.equals("textures") || sub.startsWith("textures/")) {
            Set<String> dirs = DIRS.computeIfAbsent(ns, key -> new TreeSet<>());
            dirs.add("textures/");
            if (!sub.equals("textures")) {
                int index = sub.indexOf('/');
                while (index >= 0) {
                    dirs.add(sub.substring(0, index + 1) + "/");
                    index = sub.indexOf('/', index + 1);
                }
            }
        }
    }

    private static void dispatch(String ns, String filePath, Path disk, ZipFile zip, ZipEntry entry,
        boolean fromDisk) {
        if (ALIAS_PATTERN.matcher(filePath).find()) {
            return;
        }
        if (isValidNamespace(ns) && isValidPath(filePath)) {
            ResourceLocation real = new ResourceLocation(ns, filePath);
            FILES.computeIfAbsent(ns, key -> new TreeMap<>()).putIfAbsent(filePath, real);
            if (filePath.startsWith("textures/") && filePath.endsWith(".png")) {
                Set<String> dirs = DIRS.computeIfAbsent(ns, key -> new TreeSet<>());
                dirs.add("textures/");
                int index = filePath.indexOf('/');
                while (index >= 0) {
                    dirs.add(filePath.substring(0, index + 1));
                    index = filePath.indexOf('/', index + 1);
                }
            }
            return;
        }
        if (!isValidNamespace(ns)) {
            return;
        }
        addWeirdFile(ns, filePath, disk, zip, entry, fromDisk);
    }

    private static void addWeirdFile(String ns, String filePath, Path disk, ZipFile zip, ZipEntry entry,
        boolean fromDisk) {
        try {
            byte[] bytes;
            if (disk != null) {
                bytes = Files.readAllBytes(disk);
            } else if (zip != null && entry != null) {
                InputStream stream = zip.getInputStream(entry);
                try {
                    bytes = readAll(stream);
                } finally {
                    stream.close();
                }
            } else {
                return;
            }
            String aliasPath = filePath.toLowerCase(Locale.ROOT);
            if (WEIRD.containsKey(ns + ":" + aliasPath)) {
                aliasPath = encodePath(filePath);
                if (!isValidPath(aliasPath)) {
                    return;
                }
            }
            ResourceLocation alias = new ResourceLocation(ns, aliasPath);
            FILES.computeIfAbsent(ns, key -> new TreeMap<>()).putIfAbsent(filePath, alias);
            if (filePath.startsWith("textures/") && filePath.endsWith(".png")) {
                Set<String> dirs = DIRS.computeIfAbsent(ns, key -> new TreeSet<>());
                dirs.add("textures/");
                int index = filePath.indexOf('/');
                while (index >= 0) {
                    dirs.add(filePath.substring(0, index + 1));
                    index = filePath.indexOf('/', index + 1);
                }
            }
            WEIRD.put(ns + ":" + aliasPath, new Weird(filePath, bytes, fromDisk));
            WEIRD_LOWER.putIfAbsent(ns + ":" + filePath.toLowerCase(Locale.ROOT), aliasPath);
            WEIRD_STRIPPED.putIfAbsent(ns + ":" + stripPath(filePath), aliasPath);
        } catch (Exception ignored) {
        }
    }

    private static void mergePackDisk() {
        try {
            Path assets = CustomSkinsPack.assets();
            Path textures = assets.resolve(CustomSkinsPack.NAMESPACE).resolve("textures");
            if (Files.isDirectory(textures)) {
                Set<String> dirs = DIRS.computeIfAbsent(CustomSkinsPack.NAMESPACE,
                    key -> new TreeSet<>());
                dirs.add("textures/");
                try (Stream<Path> walk = Files.walk(textures)) {
                    walk.filter(Files::isDirectory).forEach(path -> {
                        String rel = textures.relativize(path).toString().replace('\\', '/');
                        dirs.add(rel.isEmpty() ? "textures/" : "textures/" + rel + "/");
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean materializeWeird() {
        if (WEIRD.isEmpty()) {
            return false;
        }
        boolean wrote = false;
        for (Map.Entry<String, Weird> entry : WEIRD.entrySet()) {
            String key = entry.getKey();
            int colon = key.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String ns = key.substring(0, colon);
            String aliasPath = key.substring(colon + 1);
            byte[] bytes = entry.getValue().bytes();
            try {
                Path target;
                if (entry.getValue().fromDisk()) {
                    target = CustomSkinsPack.mirror(ns, aliasPath);
                } else {
                    dropPlayerResidue(ns, aliasPath, bytes);
                    target = CustomSkinsPack.builtin(ns, aliasPath);
                }
                if (Files.isRegularFile(target)
                    && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) {
                    MATERIALIZED.add(key);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                MATERIALIZED.add(key);
                wrote = true;
            } catch (Exception ignored) {
            }
        }
        return wrote;
    }

    private static void dropPlayerResidue(String ns, String aliasPath, byte[] bytes) {
        try {
            Path residue = CustomSkinsPack.mirror(ns, aliasPath);
            if (Files.isRegularFile(residue) && java.util.Arrays.equals(Files.readAllBytes(residue), bytes)) {
                Files.delete(residue);
            }
        } catch (Exception ignored) {
        }
    }

    private static void registerWeird() {
        if (WEIRD.isEmpty()) {
            return;
        }
        try {
            TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
            for (String key : WEIRD.keySet()) {
                if (MATERIALIZED.contains(key)) {
                    continue;
                }
                int colon = key.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                try {
                    ResourceLocation alias =
                        new ResourceLocation(key.substring(0, colon), key.substring(colon + 1));
                    BufferedImage image =
                        ImageIO.read(new ByteArrayInputStream(WEIRD.get(key).bytes()));
                    if (image != null) {
                        textureManager.loadTexture(alias, new DynamicTexture(image));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String encodePath(String path) {
        byte[] bytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(bytes.length + 16);
        for (byte value : bytes) {
            char c = (char) (value & 0xFF);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '.' || c == '/' || c == '-') {
                builder.append(c);
            } else {
                builder.append("_u");
                builder.append(HEX.charAt((value >> 4) & 0xF));
                builder.append(HEX.charAt(value & 0xF));
                builder.append('_');
            }
        }
        return builder.toString();
    }

    static boolean isValidNamespace(String ns) {
        if (ns == null || ns.isEmpty()) {
            return false;
        }
        for (int i = 0; i < ns.length(); i++) {
            char c = ns.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidPath(String path) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '/' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String stripPath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/:]", "");
    }

    public static List<String> namespaces() {
        List<String> list = new ArrayList<>(DIRS.keySet());
        Collections.sort(list);
        return list;
    }

    public static boolean hasDir(String namespace, String dir) {
        Set<String> dirs = DIRS.get(namespace);
        return dirs != null && dir != null && dirs.contains(dir);
    }

    public static boolean hasFile(String namespace, String path) {
        TreeMap<String, ResourceLocation> all = FILES.get(namespace);
        return all != null && path != null && all.containsKey(path);
    }

    public static ResourceLocation rlFor(String namespace, String path) {
        TreeMap<String, ResourceLocation> all = FILES.get(namespace);
        if (all == null || path == null) {
            return null;
        }
        ResourceLocation location = all.get(path);
        if (location == null) {
            location = lookupIgnoreCase(all, path);
        }
        if (location != null) {
            return location;
        }
        if (WEIRD.containsKey(namespace + ":" + path)) {
            if (isValidNamespace(namespace) && isValidPath(path)) {
                return new ResourceLocation(namespace, path);
            }
            return null;
        }
        return null;
    }

    public static String toOriginal(String namespace, String path) {
        Weird weird = WEIRD.get(namespace + ":" + path);
        return weird == null ? path : weird.originalPath();
    }

    public static String toDisplay(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String namespace = CustomSkinsPack.NAMESPACE;
        String path = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        Weird weird = WEIRD.get(namespace + ":" + path);
        if (weird != null) {
            return colon >= 0 ? namespace + ":" + weird.originalPath() : weird.originalPath();
        }
        String original = originalIgnoreCase(namespace, path);
        if (original != null) {
            return colon >= 0 ? namespace + ":" + original : original;
        }
        return value;
    }

    public static byte[] weirdBytes(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        Weird weird = WEIRD.get(location.getNamespace() + ":" + location.getPath());
        return weird == null ? null : weird.bytes();
    }

    public static String resolveAlias(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String namespace = CustomSkinsPack.NAMESPACE;
        String path = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        if (path.isEmpty()) {
            return null;
        }
        String alias = WEIRD_LOWER.get(namespace + ":" + path.toLowerCase(Locale.ROOT));
        if (alias == null) {
            alias = WEIRD_STRIPPED.get(namespace + ":" + stripPath(path));
        }
        if (alias != null) {
            return namespace + ":" + alias;
        }
        TreeMap<String, ResourceLocation> all = FILES.get(namespace);
        if (all == null) {
            return null;
        }
        ResourceLocation location = all.get(path);
        if (location == null) {
            location = lookupIgnoreCase(all, path);
        }
        if (location == null) {
            return null;
        }
        return location.getPath().equals(path) ? null : namespace + ":" + location.getPath();
    }

    private static ResourceLocation lookupIgnoreCase(TreeMap<String, ResourceLocation> all,
        String path) {
        for (Map.Entry<String, ResourceLocation> entry : all.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String originalIgnoreCase(String namespace, String path) {
        TreeMap<String, ResourceLocation> all = FILES.get(namespace);
        if (all == null) {
            return null;
        }
        for (String key : all.keySet()) {
            if (!key.equals(path) && key.equalsIgnoreCase(path)) {
                return key;
            }
        }
        return null;
    }

    public static List<String> children(String namespace, String parent) {
        List<String> result = new ArrayList<>();
        Set<String> dirs = DIRS.get(namespace);
        if (dirs == null || parent == null) {
            return result;
        }
        for (String dir : dirs) {
            if (!dir.startsWith(parent) || dir.length() <= parent.length()) {
                continue;
            }
            String rest = dir.substring(parent.length());
            int slash = rest.indexOf('/');
            if (slash != rest.length() - 1) {
                continue;
            }
            if (slash <= 0) {
                continue;
            }
            result.add(rest.substring(0, slash));
        }
        return result;
    }

    public static List<ResourceLocation> filesIn(String namespace, String dir) {
        List<ResourceLocation> result = new ArrayList<>();
        TreeMap<String, ResourceLocation> all = FILES.get(namespace);
        if (all == null || dir == null) {
            return result;
        }
        for (Map.Entry<String, ResourceLocation> entry : all.entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith(dir)) {
                continue;
            }
            String rest = path.substring(dir.length());
            if (rest.isEmpty() || rest.indexOf('/') >= 0) {
                continue;
            }
            result.add(entry.getValue());
        }
        return result;
    }
}
