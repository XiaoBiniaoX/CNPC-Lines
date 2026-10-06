package top.cnpclines.client.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class Pinyin {

    private static final Map<Character, String> TABLE = new HashMap<>();
    private static boolean loaded;

    private Pinyin() {
    }

    static String of(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        ensureLoaded();
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String pinyin = TABLE.get(c);
            builder.append(pinyin != null ? pinyin : c);
        }
        return builder.toString();
    }

    static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    static boolean fuzzy(String query, String target) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        if (target == null || target.isEmpty()) {
            return false;
        }
        int index = 0;
        for (int i = 0; i < target.length() && index < query.length(); i++) {
            if (query.charAt(index) == target.charAt(i)) {
                index++;
            }
        }
        return index == query.length();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream stream =
            Pinyin.class.getResourceAsStream("/assets/cnpclines/pinyin/hanzi.txt")) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() < 3 || line.charAt(1) != '\t') {
                        continue;
                    }
                    TABLE.put(line.charAt(0), line.substring(2));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
