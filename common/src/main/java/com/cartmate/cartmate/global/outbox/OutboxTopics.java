package com.cartmate.cartmate.global.outbox;

import java.util.Locale;

public final class OutboxTopics {

    static final String PREFIX = "cartmate";
    static final String SHARED_PACKAGE = "com.cartmate.cartmate.shared";
    private static final String EVENT_SUFFIX = "Event";

    private OutboxTopics() {
    }

    public static String of(Class<?> eventType) {
        return PREFIX + "." + moduleOf(eventType) + "." + nameOf(eventType);
    }

    static String moduleOf(Class<?> eventType) {
        String packageName = eventType.getPackageName();
        String prefix = SHARED_PACKAGE + ".";
        if (!packageName.startsWith(prefix) || !packageName.endsWith(".event")) {
            throw new IllegalArgumentException(
                    "이벤트는 " + SHARED_PACKAGE + ".<모듈>.event 패키지에 있어야 한다: " + eventType.getName());
        }
        String module = packageName.substring(prefix.length(), packageName.length() - ".event".length());
        if (module.isEmpty() || module.contains(".")) {
            throw new IllegalArgumentException(
                    "이벤트는 " + SHARED_PACKAGE + ".<모듈>.event 패키지에 있어야 한다: " + eventType.getName());
        }
        return module;
    }

    static String nameOf(Class<?> eventType) {
        String name = eventType.getSimpleName();
        if (name.endsWith(EVENT_SUFFIX) && name.length() > EVENT_SUFFIX.length()) {
            name = name.substring(0, name.length() - EVENT_SUFFIX.length());
        }
        return toKebabCase(name);
    }

    private static String toKebabCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
