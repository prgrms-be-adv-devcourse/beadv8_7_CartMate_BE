package com.cartmate.cartmate.boundedContext.trust.domain;

// 거래 이력 집계값으로 신뢰도 점수와 등급을 계산한다.
public final class TrustScorePolicy {

    public static final int MAX_SCORE = 100;
    public static final int MIN_SAMPLE_COUNT = 5;

    private static final int FAULT_RATE_WEIGHT = 200;

    private static final int HIGH_THRESHOLD = 90;    // 귀책 클레임 비율 5% 이하
    private static final int NORMAL_THRESHOLD = 70;  // 귀책 클레임 비율 15% 이하

    private TrustScorePolicy() {
    }

    // 점수 = 100 - (귀책 클레임 비율 x 200) 이며 0~100으로 보정한다. 귀책 클레임 비율은 구매확정 건수 대비로 구한다.
    // 구매확정이 MIN_SAMPLE_COUNT 미만이면 점수를 매기지 않고 점수 없는(null) NEW 등급을 반환한다.
    public static TrustEvaluation evaluate(TrustMetrics metrics) {
        if (metrics.confirmedCount() < MIN_SAMPLE_COUNT) {
            return new TrustEvaluation(null, TrustLevel.NEW);
        }

        double faultRate = rate(metrics.faultClaimCount(), metrics.confirmedCount());
        int score = (int) Math.round(MAX_SCORE - faultRate * FAULT_RATE_WEIGHT);
        score = Math.max(0, Math.min(MAX_SCORE, score));

        return new TrustEvaluation(score, levelOf(score));
    }

    private static double rate(int count, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.min(1.0, (double) count / total);
    }

    private static TrustLevel levelOf(int score) {
        if (score >= HIGH_THRESHOLD) {
            return TrustLevel.HIGH;
        }
        if (score >= NORMAL_THRESHOLD) {
            return TrustLevel.NORMAL;
        }
        return TrustLevel.LOW;
    }
}
