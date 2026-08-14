package com.prizm.search.profile;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchTokenNormalizer {

    private static final Pattern SPRING_BOOT_FORMATTING = Pattern.compile(
            "(?<![a-z0-9+#.])spring(?:[\\p{Zs}\\t_-]*)boot(?![a-z0-9+#.])");
    private static final Pattern SPRING_BOOT_QUERY_FORMATTING = Pattern.compile(
            "(?i)(?<![a-z0-9+#.])spring(?:[\\p{Zs}\\t_-]*)boot(?![a-z0-9+#.])");

    private SearchTokenNormalizer() {
    }

    public static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return SPRING_BOOT_FORMATTING.matcher(normalized).replaceAll("springboot");
    }

    public static String canonicalizeTechnologyNames(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return SPRING_BOOT_QUERY_FORMATTING.matcher(normalized).replaceAll("Spring Boot");
    }
}
