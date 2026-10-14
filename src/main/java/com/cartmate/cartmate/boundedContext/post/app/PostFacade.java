package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.PostSeller;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostFacade {
    private final PostSyncSellerUseCase postSyncSellerUseCase;

    @Transactional
    public PostSeller syncSeller(SellerDto seller) {
        return postSyncSellerUseCase.syncSeller(seller);
    }

}
