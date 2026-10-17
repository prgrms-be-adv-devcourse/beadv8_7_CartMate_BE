package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import com.cartmate.cartmate.shared.post.dto.SalesPostRollbackRequest;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.ProductRollbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
    private final SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    private final SalesPostCreateUseCase salesPostCreateUseCase;
    private final SalesPostUpdateUseCase salesPostUpdateUseCase;
    private final SalesPostDeleteUseCase salesPostDeleteUseCase;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;

    @Transactional
    public SalesPostSeller syncSeller(SellerDto seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
    }

    public void registerSalesPost(CreateSalesPostRequest request) {
        CreateProductRequest productRequest = transactionTemplate.execute(status -> {
            SalesPost post = salesPostCreateUseCase.saveSalesPost(request);
            return new CreateProductRequest(post.getId(), request.toCreateProductList());
        });

        try{
            salesPostApiClient.registerProducts(productRequest);
        } catch(ServiceException e) {
            salesPostDeleteUseCase.deleteSalesPostByEvent(productRequest.getPostId());
            GlobalConfig.getEventPublisher().publish(
                    new PostDeletedEvent(
                            new SalesPostRollbackRequest(productRequest.getPostId())
                    )
            );
            throw e;
        }
    }

    @Transactional
    public void deleteSalesPostByUser(Long postId) {
        salesPostDeleteUseCase.deleteSalesPostByUser(postId);
    }

}
