package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sales-posts")
public class SalesPostController {
    private final SalesPostFacade salesPostFacade;


    @PostMapping
    public ResponseEntity<Void> registerSalesPost(
            @RequestPart("request") CreateSalesPostRequest request,
            @RequestPart("images") List<MultipartFile> images) {
        salesPostFacade.registerSalesPost(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<RsData<PageResponse<SelectSalesPostResponse>>> selectSalesPostAll(
            SelectSalesPostRequest request,
            Pageable pageable) {
        RsData<PageResponse<SelectSalesPostResponse>> result = salesPostFacade.selectSalesPostAll(request, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/detail/{postId}")
    public ResponseEntity<RsData<SelectSalesPostResponse>> selectSalesPostDetail(@PathVariable Long postId) {
        RsData<SelectSalesPostResponse> result = salesPostFacade.selectSalesPostDetail(postId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/sellers/{sellerId}/posts/{postId}")
    public ResponseEntity<Void> deleteSalesPost(
            @RequestParam Long sellerId,
            @RequestParam Long postId
    ) {
        salesPostFacade.deleteSalesPostByUser(postId, sellerId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PatchMapping("/sellers/{sellerId}/posts/{postId}/pause")
    @Operation(operationId = "pauseSalesPost", summary = "판매자 판매 중지")
    public RsData<Void> pause(@PathVariable Long sellerId, @PathVariable Long postId,
                              @AuthenticationPrincipal SecurityUser user) {
        salesPostFacade.pauseSalesPostBySeller(postId, sellerId, user == null ? null : user.memberId());
        return new RsData<>("200-1", "판매를 중지했습니다. 진행 중인 주문은 취소·환불됩니다.");
    }

    @PatchMapping("/sellers/{sellerId}/posts/{postId}/resume")
    @Operation(operationId = "resumeSalesPostBySeller", summary = "판매자 판매 재개")
    public RsData<Void> resume(@PathVariable Long sellerId, @PathVariable Long postId) {
        salesPostFacade.resumeSalesPostBySeller(postId, sellerId);
        return new RsData<>("200-1", "판매를 다시 시작했습니다.");
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateSalesPost(
            @RequestPart("request") UpdateSalesPostRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        salesPostFacade.updateSalesPostByUser(request, images);
        return ResponseEntity.noContent().build();
    }

}
