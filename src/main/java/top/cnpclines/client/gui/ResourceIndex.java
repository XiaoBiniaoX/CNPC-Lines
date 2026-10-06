package top.cnpclines.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final Set<String> MATERIALIZED = new HashSet<>();
    private static final Pattern ALIAS_PATTERN = Pattern.compile("_u[0-9a-f]{2}_");
    private static final String HEX = "0123456789abcdef";
    private static boolean loaded;
    private static int packCount = -1;

    private record Weird(String originalPath, byte[] bytes, boolean fromDisk) {
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
        MATERIALIZED.clear();
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
        materializeWeird();
        registerWeird();
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
        if (ALIAS_PATTERN.matcher(path).find()) {
            return;
        }
        FILES.computeIfAbsent(location.getNamespace(), key -> new TreeMap<>())
            .putIfAbsent(path, location);
        Set<String> dirs = DIRS.computeIfAbsent(location.getNamespace(), key -> new TreeSet<>());
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

    private static void addOwnFile(String ns, String filePath, Path disk,
        ZipFile zip, ZipEntry entry, boolean fromDisk) {
        if (ALIAS_PATTERN.matcher(filePath).find()) {
            return;
        }
        ResourceLocation real;
        try {
            real = new ResourceLocation(ns, filePath);
        } catch (Exception e) {
            real = null;
        }
        if (real != null) {
            FILES.computeIfAbsent(ns, key -> new TreeMap<>()).putIfAbsent(filePath, real);
            return;
        }
        try {
            byte[] bytes;
            if (disk != null) {
                bytes = Files.readAllBytes(disk);
            } else if (zip != null && entry != null) {
                try (var stream = zip.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
            } else {
                return;
            }
            String aliasPath = encodePath(filePath);
            ResourceLocation alias = new ResourceLocation(ns, aliasPath);
            FILES.computeIfAbsent(ns, key -> new TreeMap<>()).putIfAbsent(filePath, alias);
            WEIRD.put(ns + ":" + aliasPath, new Weird(filePath, bytes, fromDisk));
            WEIRD_LOWER.putIfAbsent(ns + ":" + filePath.toLowerCase(Locale.ROOT), aliasPath);
            WEIRD_STRIPPED.putIfAbsent(ns + ":" + stripPath(filePath), aliasPath);
        } catch (Exception ignored) {
        }
    }

    private static String stripPath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/:]", "");
    }

    private static void materializeWeird() {
        if (WEIRD.isEmpty()) {
            return;
        }
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
                    && Arrays.equals(Files.readAllBytes(target), bytes)) {
                    MATERIALIZED.add(key);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                MATERIALIZED.add(key);
            } catch (Exception ignored) {
            }
        }
    }

    private static void dropPlayerResidue(String ns, String aliasPath, byte[] bytes) {
        try {
            Path residue = CustomSkinsPack.mirror(ns, aliasPath);
            if (Files.isRegularFile(residue) && Arrays.equals(Files.readAllBytes(residue), bytes)) {
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
            var textureManager = Minecraft.getInstance().getTextureManager();
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
                    NativeImage image =
                        NativeImage.read(new ByteArrayInputStream(WEIRD.get(key).bytes()));
                    textureManager.register(alias, new DynamicTexture(image));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
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
                return new ResourceLocation(namespace, path);
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
        result.sort(ResourceIndex::naturalCompare);
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
        result.sort(ResourceIndex::compareDisplay);
        return result;
    }

    public static List<ResourceLocation> search(String root, String query) {
        List<ResourceLocation> result = new ArrayList<>();
        String q = Pinyin.normalizeQuery(query);
        if (q.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, TreeMap<String, ResourceLocation>> nsEntry : FILES.entrySet()) {
            for (Map.Entry<String, ResourceLocation> entry : nsEntry.getValue().entrySet()) {
                String path = entry.getKey();
                if (root != null && !root.isEmpty() && !path.startsWith(root)) {
                    continue;
                }
                if (matches(q, path)) {
                    result.add(entry.getValue());
                }
            }
        }
        result.sort(ResourceIndex::compareDisplay);
        return result;
    }

    private static boolean matches(String q, String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (Pinyin.fuzzy(q, name)) {
            return true;
        }
        if (Pinyin.fuzzy(q, Pinyin.of(name))) {
            return true;
        }
        String full = path.toLowerCase(Locale.ROOT);
        if (Pinyin.fuzzy(q, full)) {
            return true;
        }
        return Pinyin.fuzzy(q, Pinyin.of(full));
    }

    private static int compareDisplay(ResourceLocation a, ResourceLocation b) {
        String left = displayName(a);
        String right = displayName(b);
        int cmp = naturalCompare(left, right);
        return cmp != 0 ? cmp : left.compareTo(right);
    }

    private static int naturalCompare(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            char ca = left.charAt(i);
            char cb = right.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int osi = i;
                int osj = j;
                while (i < left.length() && Character.isDigit(left.charAt(i))) {
                    i++;
                }
                while (j < right.length() && Character.isDigit(right.charAt(j))) {
                    j++;
                }
                int si = osi;
                int sj = osj;
                while (si < i - 1 && left.charAt(si) == '0') {
                    si++;
                }
                while (sj < j - 1 && right.charAt(sj) == '0') {
                    sj++;
                }
                int lenA = i - si;
                int lenB = j - sj;
                if (lenA != lenB) {
                    return lenA - lenB;
                }
                for (int k = 0; k < lenA; k++) {
                    char xa = left.charAt(si + k);
                    char xb = right.charAt(sj + k);
                    if (xa != xb) {
                        return xa - xb;
                    }
                }
                int rawA = i - osi;
                int rawB = j - osj;
                if (rawA != rawB) {
                    return rawA - rawB;
                }
                continue;
            }
            char la = Character.toLowerCase(ca);
            char lb = Character.toLowerCase(cb);
            if (la != lb) {
                return la - lb;
            }
            if (ca != cb) {
                return ca - cb;
            }
            i++;
            j++;
        }
        return (left.length() - i) - (right.length() - j);
    }

    private static String displayName(ResourceLocation location) {
        String original = toOriginal(location.getNamespace(), location.getPath());
        int slash = original.lastIndexOf('/');
        return slash < 0 ? original : original.substring(slash + 1);
    }
}
