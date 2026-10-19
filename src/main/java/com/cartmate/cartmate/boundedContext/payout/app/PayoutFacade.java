package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.app.dto.PendingPayoutResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementDetailResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementInfoResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayoutFacade {

    private final PayoutCreateUseCase payoutCreateUseCase;
    private final PayoutSellerRegisterUseCase payoutSellerRegisterUseCase;
    private final PayoutPayUseCase payoutPayUseCase;
    private final PayoutQueryUseCase payoutQueryUseCase;

    // 구매확정 이벤트를 받아 정산을 만든다. 구매확정과 같은 트랜잭션에 참여해 정산 생성이 실패하면 구매확정도 롤백된다.
    @Transactional
    public void createPayouts(DeliveryConfirmedEvent event) {
        payoutCreateUseCase.create(event);
    }

    // 정산 한 건을 지급한다. 입금과 지급 완료 처리가 한 트랜잭션이라 입금이 실패하면 지급 완료도 롤백된다.
    @Transactional
    public void pay(Long payoutId) {
        payoutPayUseCase.pay(payoutId, LocalDateTime.now());
    }

    // 판매자 등록 이벤트를 받아 지급에 필요한 판매자의 회원 ID를 복제한다.
    @Transactional
    public void registerSeller(SellerRegisteredEvent event) {
        payoutSellerRegisterUseCase.register(event);
    }

    // 로그인한 판매자의 정산 내역을 지급 예정일과 상태별로 묶어 조회한다.
    @Transactional(readOnly = true)
    public List<SettlementResponse> getMySettlements(Long memberId, LocalDate startDate, LocalDate endDate) {
        return payoutQueryUseCase.getMySettlements(memberId, startDate, endDate);
    }

    // 로그인한 판매자의 정산 한 줄(정산 ID)의 합계와 묶인 판매 건 목록을 조회한다.
    @Transactional(readOnly = true)
    public SettlementDetailResponse getMySettlementDetail(Long memberId, String settlementId) {
        return payoutQueryUseCase.getMySettlementDetail(memberId, settlementId);
    }

    // 로그인한 판매자의 아직 지급되지 않은 구매확정 건을 조회한다.
    @Transactional(readOnly = true)
    public List<PendingPayoutResponse> getMyPendingPayouts(Long memberId) {
        return payoutQueryUseCase.getMyPendingPayouts(memberId);
    }

    // 로그인한 판매자의 정산 정책(수수료율, 지급일, 다음 지급일)과 정산 현황을 조회한다.
    @Transactional(readOnly = true)
    public SettlementInfoResponse getMySettlementInfo(Long memberId) {
        return payoutQueryUseCase.getMySettlementInfo(memberId, LocalDate.now());
    }
}
