package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostApiClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.RollbackProductRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostFacade {
    private final SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    private final SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    private final SalesPostCreateUseCase salesPostCreateUseCase;
    private final SalesPostSelectUseCase salesPostSelectUseCase;
    private final SalesPostUpdateUseCase salesPostUpdateUseCase;
    private final SalesPostDeleteUseCase salesPostDeleteUseCase;
    private final TransactionTemplate transactionTemplate;
    private final SalesPostApiClient salesPostApiClient;

    @Transactional
    public SalesPostSeller syncSeller(SellerDto seller) {
        return salesPostSyncSellerUseCase.syncSeller(seller);
    }

    public void registerSalesPost(CreateSalesPostRequest request, List<MultipartFile> images) {
        CreateProductRequest productRequest = transactionTemplate.execute(status -> {
            SalesPost post = salesPostCreateUseCase.saveSalesPost(request);
            return new CreateProductRequest(post.getId(), request.toCreateProductList());
        });

        try{
            salesPostApiClient.registerProducts(productRequest, images);
        } catch(ServiceException e) {
            salesPostDeleteUseCase.deleteSalesPostByEvent(productRequest.getPostId());
            throw e;
        }
    }

    @Transactional
    public void deleteSalesPostByUser(Long postId) {
        salesPostDeleteUseCase.deleteSalesPostByUser(postId);
    }

    // 스케줄러에 의한 삭제
    @Transactional
    public void deleteProductByServer(RollbackProductRequest request) {
        salesPostDeleteUseCase.deleteSalesPostByServer(request.getPostId());
    }

    @Transactional
    public void syncProduct(SyncProductRequest request) {
        salesPostSyncProductUseCase.syncProduct(request);
    }

    @Transactional(readOnly = true)
    public RsData<PageResponse<SelectSalesPostResponse>> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable) {
        return new RsData<>(
                "200-1",
                "판매목록 조회에 성공했습니다.",
                salesPostSelectUseCase.selectSalesPostAll(request, pageable)
        );
    }

    public RsData<SelectSalesPostResponse> selectSalesPostDetail(Long postId) {
        return new RsData<>(
                "200-2",
                "판매글 상세조회에 성공했습니다.",
                salesPostSelectUseCase.selectSalesPostDetail(postId)
        );
    }
}
