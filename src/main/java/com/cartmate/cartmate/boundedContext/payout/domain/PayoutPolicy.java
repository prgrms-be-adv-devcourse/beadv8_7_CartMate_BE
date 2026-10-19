package com.cartmate.cartmate.boundedContext.payout.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 정산 규칙. 수수료율과 지급일이 바뀌면 이 클래스만 고친다.
public final class PayoutPolicy {

    // 수수료율(%). 판매 금액의 3%를 수수료로 뗀다.
    public static final int FEE_RATE_PERCENT = 3;

    // 지급일. 구매확정한 달의 다음 달 이 날짜에 지급한다.
    public static final int PAYOUT_DAY_OF_MONTH = 15;

    private PayoutPolicy() {
    }

    // 판매 금액에서 뗄 수수료. 원 단위 미만은 내린다.
    public static long feeOf(long salesAmount) {
        return salesAmount * FEE_RATE_PERCENT / 100;
    }

    // 구매확정 시각이 속한 달의 다음 달 지급일 00:00을 지급 예정 시각으로 계산한다.
    public static LocalDateTime scheduledAtFor(LocalDateTime confirmedAt) {
        return confirmedAt.toLocalDate()
                .plusMonths(1)
                .withDayOfMonth(PAYOUT_DAY_OF_MONTH)
                .atTime(LocalTime.MIN);
    }

    // 지급일에 지급되는 정산의 구매확정 기간 시작일. 구매확정한 달(지급일의 전달)의 1일이다.
    public static LocalDate confirmPeriodStart(LocalDate payoutDate) {
        return payoutDate.minusMonths(1).withDayOfMonth(1);
    }

    // 지급일에 지급되는 정산의 구매확정 기간 종료일. 구매확정한 달의 말일이다.
    public static LocalDate confirmPeriodEnd(LocalDate payoutDate) {
        return payoutDate.withDayOfMonth(1).minusDays(1);
    }

    // 기준일 이후(기준일이 지급일이면 그날 포함)로 처음 돌아오는 지급일.
    public static LocalDate nextPayoutDate(LocalDate today) {
        LocalDate thisMonth = today.withDayOfMonth(PAYOUT_DAY_OF_MONTH);

        return today.isAfter(thisMonth) ? thisMonth.plusMonths(1) : thisMonth;
    }
}
