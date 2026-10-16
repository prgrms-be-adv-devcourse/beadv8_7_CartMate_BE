package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.response.ResponseData;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
    private final SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    private final SalesPostCreateDraftUseCase salesPostCreateDraftUseCase;
    private final SalesPostWritePostUseCase salesPostWritePostUseCase;
    private final SalesPostApiClient salesPostApiClient;

    @Transactional
    public SalesPostSeller syncSeller(SellerDto seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
    }

    @Transactional
    public ResponseData<CreateSalesPostResponse> registerSalesPost(CreateSalesPostRequest request) {
        SalesPost post = salesPostCreateDraftUseCase.createSalesPostDraft(request);
        CreateProductRequest productRequest = new CreateProductRequest(post.getId(), request.toCreateProductList());
        salesPostApiClient.publishProducts(productRequest);
        salesPostWritePostUseCase.publishSalesPost(post.getId());
        // 비동기 코드 이후
        return null;
    }
}
