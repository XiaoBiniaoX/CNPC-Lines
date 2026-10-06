package top.cnpclines.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import top.cnpclines.CNPCLines;
import top.cnpclines.client.CustomSkinsPack;

public final class ResourceIndex {

    private static final Map<String, TreeMap<String, ResourceLocation>> FILES = new HashMap<>();
    private static final Map<String, Set<String>> DIRS = new HashMap<>();
    private static final Map<String, Weird> WEIRD = new HashMap<>();
    private static final Map<String, String> WEIRD_LOWER = new HashMap<>();
    private static final Map<String, String> WEIRD_STRIPPED = new HashMap<>();
    private static final Set<String> DISK = new HashSet<>();
    private static final Set<String> REGISTERED = new HashSet<>();
    private static final Pattern ALIAS_PATTERN = Pattern.compile("_u[0-9a-f]{2}_");
    private static final String HEX = "0123456789abcdef";
    private static boolean loaded;
    private static int packCount = -1;

    private record Weird(String originalPath, byte[] bytes) {
    }

    private ResourceIndex() {
    }

    public static void ensureFresh() {
        int count;
        try {
            count = (int) Minecraft.getInstance().getResourceManager().listPacks().count();
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
        DISK.clear();
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            manager.listPacks().forEach(pack -> {
                try {
                    for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                        scanPack(pack, ns);
                    }
                } catch (Exception ignored) {
                }
            });
            packCount = (int) manager.listPacks().count();
        } catch (Exception e) {
            packCount = -1;
        }
        loaded = true;
        mergePackDisk();
        scanOwnModAssets();
        releaseStaleTextures();
    }

    private static void scanPack(PackResources pack, String namespace) {
        try {
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "textures",
                (location, supplier) -> addPng(location));
        } catch (Exception ignored) {
        }
    }

    private static void addPng(ResourceLocation location) {
        String path = location.getPath();
        if (!path.endsWith(".png")) {
            return;
        }
        if (!isPackSafePath(path)) {
            return;
        }
        if (ALIAS_PATTERN.matcher(path).find()) {
            return;
        }
        FILES.computeIfAbsent(location.getNamespace(), key -> new TreeMap<>())
            .putIfAbsent(path, location);
        addDirs(location.getNamespace(), path);
    }

    private static void addDirs(String ns, String path) {
        Set<String> dirs = DIRS.computeIfAbsent(ns, key -> new TreeSet<>());
        dirs.add("textures/");
        int index = path.indexOf('/');
        while (index >= 0) {
            dirs.add(path.substring(0, index + 1));
            index = path.indexOf('/', index + 1);
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
            if (Files.isDirectory(assets)) {
                try (Stream<Path> walk = Files.walk(assets)) {
                    walk.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(path -> {
                            String rel = assets.relativize(path).toString().replace('\\', '/');
                            int slash = rel.indexOf('/');
                            if (slash <= 0 || slash >= rel.length() - 1) {
                                return;
                            }
                            addOwnFile(rel.substring(0, slash), rel.substring(slash + 1),
                                path, null, null, true);
                        });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void scanOwnModAssets() {
        try {
            CodeSource source = CNPCLines.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return;
            }
            Path self = Path.of(source.getLocation().toURI());
            if (Files.isDirectory(self)) {
                Path assets = self.resolve("assets");
                if (!Files.isDirectory(assets)) {
                    return;
                }
                try (Stream<Path> walk = Files.walk(assets)) {
                    walk.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(path -> {
                            String rel = assets.relativize(path).toString().replace('\\', '/');
                            int slash = rel.indexOf('/');
                            if (slash <= 0 || slash >= rel.length() - 1) {
                                return;
                            }
                            addOwnFile(rel.substring(0, slash), rel.substring(slash + 1),
                                path, null, null, false);
                        });
                }
            } else if (Files.isRegularFile(self)) {
                try (ZipFile zip = new ZipFile(self.toFile())) {
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
                        addOwnFile(rel.substring(0, slash), rel.substring(slash + 1),
                            null, zip, entry, false);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isPackSafePath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '/' || c == '.' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static void addOwnFile(String ns, String filePath, Path disk,
        ZipFile zip, ZipEntry entry, boolean fromDisk) {
        if (ALIAS_PATTERN.matcher(filePath).find()) {
            return;
        }
        addDirs(ns, filePath);
        if (isPackSafePath(filePath)) {
            try {
                ResourceLocation real = ResourceLocation.fromNamespaceAndPath(ns, filePath);
                FILES.computeIfAbsent(ns, key -> new TreeMap<>()).putIfAbsent(filePath, real);
                if (fromDisk && !REGISTERED.contains(ns + ":" + filePath)) {
                    byte[] bytes = readFile(disk, zip, entry);
                    if (bytes != null) {
                        registerTexture(ns, filePath, bytes);
                    }
                }
                return;
            } catch (Exception ignored) {
            }
        }
        byte[] bytes = readFile(disk, zip, entry);
        if (bytes == null) {
            return;
        }
        try {
            String aliasPath = encodePath(filePath);
            ResourceLocation alias = ResourceLocation.fromNamespaceAndPath(ns, aliasPath);
            FILES.computeIfAbsent(ns, key -> new TreeMap<>()).put(filePath, alias);
            WEIRD.put(ns + ":" + aliasPath, new Weird(filePath, bytes));
            WEIRD_LOWER.putIfAbsent(ns + ":" + filePath.toLowerCase(Locale.ROOT), aliasPath);
            WEIRD_STRIPPED.putIfAbsent(ns + ":" + stripPath(filePath), aliasPath);
            if (fromDisk) {
                DISK.add(ns + ":" + filePath);
            }
            registerTexture(ns, filePath, bytes);
            registerTexture(ns, aliasPath, bytes);
        } catch (Exception ignored) {
        }
    }

    private static byte[] readFile(Path disk, ZipFile zip, ZipEntry entry) {
        try {
            if (disk != null) {
                return Files.readAllBytes(disk);
            }
            if (zip != null && entry != null) {
                try (var stream = zip.getInputStream(entry)) {
                    return stream.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void registerTexture(String ns, String path, byte[] bytes) {
        try {
            String key = ns + ":" + path;
            if (REGISTERED.contains(key)) {
                return;
            }
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(ns, path);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            Minecraft.getInstance().getTextureManager()
                .register(location, new DynamicTexture(image));
            REGISTERED.add(key);
        } catch (Exception ignored) {
        }
    }

    private static void releaseStaleTextures() {
        if (REGISTERED.isEmpty()) {
            return;
        }
        try {
            Set<String> valid = new HashSet<>(DISK);
            for (Map.Entry<String, Weird> entry : WEIRD.entrySet()) {
                String key = entry.getKey();
                valid.add(key);
                int colon = key.indexOf(':');
                if (colon > 0) {
                    valid.add(key.substring(0, colon) + ":" + entry.getValue().originalPath());
                }
            }
            Set<String> stale = new HashSet<>();
            for (String key : REGISTERED) {
                if (!valid.contains(key)) {
                    stale.add(key);
                }
            }
            if (stale.isEmpty()) {
                return;
            }
            var textureManager = Minecraft.getInstance().getTextureManager();
            for (String key : stale) {
                int colon = key.indexOf(':');
                if (colon > 0) {
                    try {
                        textureManager.release(ResourceLocation.fromNamespaceAndPath(
                            key.substring(0, colon), key.substring(colon + 1)));
                    } catch (Exception ignored) {
                    }
                }
                REGISTERED.remove(key);
            }
        } catch (Exception ignored) {
        }
    }

    private static String stripPath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/:]", "");
    }

    static String encodePath(String path) {
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
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
            try {
                return ResourceLocation.fromNamespaceAndPath(namespace, path);
            } catch (Exception e) {
                return null;
            }
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
        return location == null ? null : weirdBytes(location.toString());
    }

    public static byte[] weirdBytes(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        int colon = value.indexOf(':');
        String ns = colon >= 0 ? value.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        Weird weird = WEIRD.get(ns + ":" + path);
        if (weird == null) {
            String alias = WEIRD_LOWER.get(ns + ":" + path.toLowerCase(Locale.ROOT));
            if (alias == null) {
                alias = WEIRD_STRIPPED.get(ns + ":" + stripPath(path));
            }
            if (alias != null) {
                weird = WEIRD.get(ns + ":" + alias);
            }
        }
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
