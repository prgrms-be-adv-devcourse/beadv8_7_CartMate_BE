package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerQueryAdapter implements SellerQuery {

    private final SellerRepository sellerRepository;

    @Override
    public Optional<Long> findSellerIdByMemberId(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return sellerRepository.findByMemberId(memberId)
                .map(seller -> seller.getId());
    }
}
