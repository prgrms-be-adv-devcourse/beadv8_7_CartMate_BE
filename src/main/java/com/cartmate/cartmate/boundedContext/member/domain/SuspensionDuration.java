package com.cartmate.cartmate.boundedContext.member.domain;

public enum SuspensionDuration {
    DAYS_7(7), DAYS_30(30), PERMANENT(null);

    private final Integer days;

    SuspensionDuration(Integer days) {
        this.days = days;
    }

    public Integer getDays() {
        return days;
    }
}