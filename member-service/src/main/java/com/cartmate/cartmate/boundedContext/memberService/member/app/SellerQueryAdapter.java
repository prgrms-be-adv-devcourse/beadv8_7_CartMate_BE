package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.out.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 회원에 연결된 판매자 ID 조회. 다른 서버(배송 등)는 회원 내부 API(/internal/members/{id}/seller-id)로 묻는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerQueryAdapter {

    private final SellerRepository sellerRepository;

    public Optional<Long> findSellerIdByMemberId(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return sellerRepository.findByMemberId(memberId)
                .map(seller -> seller.getId());
    }
}
