package com.cartmate.cartmate.global.outbox;

import java.util.Locale;

/**
 * 토픽 이름 규칙: {@code cartmate.<발행 모듈>.<이벤트 kebab-case>}.
 * 모듈은 이벤트 클래스의 패키지 {@code com.cartmate.cartmate.boundedContext.<서비스>.shared.<모듈>.event}에서,
 * 이름은 클래스 단순 이름에서 끝의 {@code Event}를 뗀 뒤 kebab-case로 바꾼 것.
 * 예: {@code orderService.shared.order.event.OrderPaidEvent} → {@code cartmate.order.order-paid}.
 */
public final class OutboxTopics {

    static final String PREFIX = "cartmate";
    static final String EVENT_BASE_PACKAGE = "com.cartmate.cartmate.boundedContext";
    private static final String EVENT_SUFFIX = "Event";

    private OutboxTopics() {
    }

    public static String of(Class<?> eventType) {
        return PREFIX + "." + moduleOf(eventType) + "." + nameOf(eventType);
    }

    static String moduleOf(Class<?> eventType) {
        String packageName = eventType.getPackageName();
        String prefix = EVENT_BASE_PACKAGE + ".";
        // 남는 부분은 <서비스>.shared.<모듈>.event 네 칸이어야 한다
        String[] parts = packageName.startsWith(prefix) ? packageName.substring(prefix.length()).split("\\.") : new String[0];
        if (parts.length != 4 || !parts[1].equals("shared") || !parts[3].equals("event")) {
            throw new IllegalArgumentException(
                    "이벤트는 " + EVENT_BASE_PACKAGE + ".<서비스>.shared.<모듈>.event 패키지에 있어야 한다: " + eventType.getName());
        }
        return parts[2];
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
