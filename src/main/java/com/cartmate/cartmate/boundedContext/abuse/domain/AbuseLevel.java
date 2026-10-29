package com.cartmate.cartmate.boundedContext.abuse.domain;

public enum AbuseLevel {
    NONE,
    CAUTION,
    SUSPICIOUS,
    RESTRICTED;

    public static AbuseLevel max(AbuseLevel a, AbuseLevel b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
