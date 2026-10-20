package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerRepository;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SalesPostSyncSellerUseCase {
    private final SalesPostSellerRepository salesPostSellerRepository;

    @Transactional
    public SalesPostSeller syncSeller(SellerRegisteredEvent seller) {

        SalesPostSeller newSeller = new SalesPostSeller(
                seller.sellerId(),
                seller.memberId(),
                seller.sellerType(),
                seller.displayName(),
                seller.businessNumber(),
                seller.phoneNumber(),
                seller.bankName(),
                seller.accountHolder(),
                seller.accountNumber(),
                seller.status(),
                seller.createDate(),
                seller.modifyDate()
        );

        return salesPostSellerRepository.save(newSeller);
    }
}
