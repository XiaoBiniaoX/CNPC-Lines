package top.cnpclines.client.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import top.cnpclines.client.gui.SearchResultView.Row;

public final class SkinSearch {

    private static final int MAX_RESULTS = 100;
    private static Map<Character, List<String>> pinyinCache;

    private SkinSearch() {
    }

    public static int tabForPath(String dirId) {
        String path = EntryTreeView.pathOf(dirId);
        if (path == null) {
            return -1;
        }
        if (path.startsWith("textures/cloak/")) {
            return 1;
        }
        if (path.startsWith("textures/overlays/")) {
            return 2;
        }
        if (path.startsWith("textures/entity/")) {
            return 0;
        }
        return -1;
    }

    public static List<Row> query(String raw, String scopeDirId) {
        String q = raw == null ? "" : raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return Collections.emptyList();
        }
        String scopeNs = null;
        String scopePath = "";
        if (scopeDirId != null) {
            int colon = scopeDirId.indexOf(':');
            if (colon > 0) {
                scopeNs = scopeDirId.substring(0, colon);
                scopePath = scopeDirId.substring(colon + 1);
            }
        }
        List<Scored> hits = new ArrayList<>();
        for (ResourceIndex.IndexedFile file : ResourceIndex.allFiles()) {
            if (scopeNs != null && !scopeNs.equals(file.namespace())) {
                continue;
            }
            if (!scopePath.isEmpty() && !file.path().startsWith(scopePath)) {
                continue;
            }
            String path = file.path();
            String name = stripPng(nameOf(path));
            if (name.isEmpty()) {
                continue;
            }
            String nameLower = name.toLowerCase(Locale.ROOT);
            String dirId = file.namespace() + ":" + TextureEntryList.parentDir(path);
            String dirLower = dirId.toLowerCase(Locale.ROOT);
            int score = score(name, nameLower, dirLower, q);
            if (score < 0) {
                continue;
            }
            String displayDir = EntryTreeView.displayPath(dirId);
            Row row = new Row(file.location(), dirId, nameOf(path), displayDir);
            hits.add(new Scored(score, row));
        }
        hits.sort(Comparator.comparingInt((Scored s) -> s.score)
            .thenComparing(h -> h.row.name, ResourceIndex::compareNames));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < hits.size() && i < MAX_RESULTS; i++) {
            rows.add(hits.get(i).row);
        }
        return rows;
    }

    private static int score(String name, String nameLower, String dirLower, String q) {
        if (nameLower.equals(q)) {
            return 0;
        }
        if (nameLower.startsWith(q)) {
            return 1;
        }
        if (nameLower.contains(q)) {
            return 2;
        }
        String py = fullPinyin(name);
        if (py.contains(q)) {
            return 3;
        }
        String ini = initials(name);
        if (!ini.isEmpty() && ini.contains(q)) {
            return 4;
        }
        if (dirLower.contains(q)) {
            return 5;
        }
        if (q.length() <= name.length() * 6 && dpMatch(name, q, false)) {
            return 6;
        }
        if (q.length() <= name.length() && dpMatch(name, q, true)) {
            return 7;
        }
        return -1;
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String stripPng(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String fullPinyin(String name) {
        StringBuilder builder = new StringBuilder(name.length() * 3);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isAsciiToken(c)) {
                builder.append(Character.toLowerCase(c));
                continue;
            }
            List<String> readings = pinyin().get(c);
            if (readings != null && !readings.isEmpty()) {
                builder.append(readings.get(0));
            }
        }
        return builder.toString();
    }

    private static String initials(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isAsciiToken(c)) {
                builder.append(Character.toLowerCase(c));
                continue;
            }
            List<String> readings = pinyin().get(c);
            if (readings != null && !readings.isEmpty()) {
                builder.append(readings.get(0).charAt(0));
            }
        }
        return builder.toString();
    }

    private static boolean dpMatch(String name, String q, boolean initialMode) {
        byte[][] memo = new byte[name.length() + 1][q.length() + 1];
        return dp(name, 0, q, 0, initialMode, memo);
    }

    private static boolean dp(String name, int i, String q, int j,
        boolean initialMode, byte[][] memo) {
        if (j >= q.length()) {
            return true;
        }
        if (i >= name.length()) {
            return false;
        }
        if (memo[i][j] != 0) {
            return memo[i][j] == 1;
        }
        boolean ok = dp(name, i + 1, q, j, initialMode, memo);
        if (!ok) {
            for (String token : tokens(name.charAt(i), initialMode)) {
                if (!token.isEmpty() && q.startsWith(token, j)
                    && dp(name, i + 1, q, j + token.length(), initialMode, memo)) {
                    ok = true;
                    break;
                }
            }
        }
        memo[i][j] = ok ? (byte) 1 : (byte) 2;
        return ok;
    }

    private static List<String> tokens(char c, boolean initialMode) {
        if (isAsciiToken(c)) {
            return Collections.singletonList(String.valueOf(Character.toLowerCase(c)));
        }
        List<String> readings = pinyin().get(c);
        if (readings == null || readings.isEmpty()) {
            return Collections.singletonList(String.valueOf(c));
        }
        List<String> tokens = new ArrayList<>(readings.size());
        for (String reading : readings) {
            String token = initialMode && !reading.isEmpty()
                ? reading.substring(0, 1) : reading;
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isAsciiToken(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static Map<Character, List<String>> pinyin() {
        Map<Character, List<String>> map = pinyinCache;
        if (map == null) {
            synchronized (SkinSearch.class) {
                if (pinyinCache == null) {
                    pinyinCache = loadPinyin();
                }
                map = pinyinCache;
            }
        }
        return map;
    }

    private static Map<Character, List<String>> loadPinyin() {
        Map<Character, List<String>> map = new HashMap<>();
        try (InputStream in = SkinSearch.class
            .getResourceAsStream("/assets/cnpclines/pinyin_dict.txt")) {
            if (in == null) {
                return map;
            }
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String reading = line.substring(0, tab).trim();
                String chars = line.substring(tab + 1);
                for (int i = 0; i < chars.length(); i++) {
                    List<String> list = map.get(chars.charAt(i));
                    if (list == null) {
                        list = new ArrayList<>(2);
                        map.put(chars.charAt(i), list);
                    }
                    list.add(reading);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private static final class Scored {

        final int score;
        final Row row;

        Scored(int score, Row row) {
            this.score = score;
            this.row = row;
        }
    }
}
