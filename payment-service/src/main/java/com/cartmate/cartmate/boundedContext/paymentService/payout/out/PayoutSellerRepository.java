package com.cartmate.cartmate.boundedContext.paymentService.payout.out;

import com.cartmate.cartmate.boundedContext.paymentService.payout.domain.PayoutSeller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayoutSellerRepository extends JpaRepository<PayoutSeller, Long> {

    // 로그인한 회원의 판매자 ID 찾기 (정산 화면). 판매자 등록 이벤트로 받아 둔 복제본이라 회원 모듈에 묻지 않는다.
    Optional<PayoutSeller> findByMemberId(Long memberId);
}
