package com.cartmate.cartmate.boundedContext.payout.domain;

import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

// 정산 내역 한 줄의 키. 지급 예정일과 상태가 같은 정산을 한 줄로 묶으므로 이 둘이 정산 ID가 된다. 예: 20261115-COMPLETED
public record SettlementKey(LocalDate settlementDate, PayoutStatus status) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SEPARATOR = "-";

    // 화면에 나오는 상태. 아직 쓰지 않는 CANCELED, FAILED는 정산 ID로 쓸 수 없다.
    private static final List<PayoutStatus> LISTED_STATUSES = List.of(PayoutStatus.PENDING, PayoutStatus.COMPLETED);

    public static SettlementKey parse(String settlementId) {
        if (settlementId == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID);
        }

        String[] parts = settlementId.split(SEPARATOR, -1);

        if (parts.length != 2) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID);
        }

        try {
            PayoutStatus status = PayoutStatus.valueOf(parts[1]);

            if (!LISTED_STATUSES.contains(status)) {
                throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID);
            }

            return new SettlementKey(LocalDate.parse(parts[0], DATE_FORMAT), status);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID);
        }
    }

    public String id() {
        return settlementDate.format(DATE_FORMAT) + SEPARATOR + status;
    }
}
