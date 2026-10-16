package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
    private final SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    private final SalesPostCreateDraftUseCase salesPostCreateDraftUseCase;
    private final SalesPostWritePostUseCase salesPostWritePostUseCase;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;

    @Transactional
    public SalesPostSeller syncSeller(SellerDto seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
    }

    public void registerSalesPost(CreateSalesPostRequest request) {
        // DB 작업
        CreateProductRequest productRequest = transactionTemplate.execute(status -> {
            SalesPost post = salesPostCreateDraftUseCase.createSalesPostDraft(request);
            salesPostWritePostUseCase.publishSalesPost(post.getId());
            return new CreateProductRequest(post.getId(), request.toCreateProductList());
        });

        // 이게 API 호출이라 이걸 비동기로 할거임
        salesPostApiClient.publishProducts(productRequest);
    }
}
