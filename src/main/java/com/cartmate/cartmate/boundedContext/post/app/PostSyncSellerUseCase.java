package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.PostSeller;
import com.cartmate.cartmate.boundedContext.post.out.PostSellerRepository;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PostSyncSellerUseCase {
    private final PostSellerRepository postSellerRepository;

    @Transactional
    public PostSeller syncSeller(SellerDto sellerDto) {
        PostSeller newSeller = new PostSeller(
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
                sellerDto.getCreateDate(),
                sellerDto.getModifyDate()
        );

        return postSellerRepository.save(newSeller);
    }
}
