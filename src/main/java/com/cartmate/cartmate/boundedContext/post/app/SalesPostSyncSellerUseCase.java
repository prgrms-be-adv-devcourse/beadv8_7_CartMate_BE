package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerRepository;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SalesPostSyncSellerUseCase {
    private final SalesPostSellerRepository salesPostSellerRepository;

    @Transactional
    public SalesPostSeller syncSeller(SellerDto sellerDto) {
        SalesPostSeller newSeller = new SalesPostSeller(
                sellerDto.getId(),
                sellerDto.getMemberId(),
                sellerDto.getSellerType(),
                sellerDto.getBusinessName(),
                sellerDto.getBusinessNumber(),
                sellerDto.getPhoneNumber(),
                sellerDto.getBankName(),
                sellerDto.getAccountHolder(),
                sellerDto.getAccountNumber(),
                sellerDto.getStatus(),
                sellerDto.getCreatedAt(),
                sellerDto.getUpdatedAt()
        );

        return salesPostSellerRepository.save(newSeller);
    }
}
