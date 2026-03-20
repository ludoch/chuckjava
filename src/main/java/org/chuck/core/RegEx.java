package org.chuck.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RegEx: Pattern matching using java.util.regex.
 */
public class RegEx extends ChuckObject {
    public RegEx() {
        super(ChuckType.OBJECT);
    }

    public static int match(String pattern, String text) {
        if (pattern == null || text == null) return 0;
        return Pattern.compile(pattern).matcher(text).find() ? 1 : 0;
    }

    public static String replace(String pattern, String replacement, String text) {
        if (pattern == null || replacement == null || text == null) return text;
        return text.replaceAll(pattern, replacement);
    }

    public static String[] matchAll(String pattern, String text) {
        if (pattern == null || text == null) return new String[0];
        Matcher m = Pattern.compile(pattern).matcher(text);
        java.util.List<String> results = new java.util.ArrayList<>();
        while (m.find()) {
            results.add(m.group());
        }
        return results.toArray(new String[0]);
    }
}
