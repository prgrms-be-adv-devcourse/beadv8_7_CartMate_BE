package com.cartmate.cartmate.boundedContext.post.in.admin;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostCountResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostDetailResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.AdminSalesPostSummaryResponse;
import com.cartmate.cartmate.boundedContext.post.dto.admin.SalesPostSanctionRequest;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 관리자 판매글 관리. /api/admin/** 는 보안 설정에서 ADMIN만 접근 가능.
@RestController
@RequestMapping("/api/admin/sales-posts")
@RequiredArgsConstructor
public class AdminSalesPostController {
    private final SalesPostFacade salesPostFacade;

    @GetMapping
    @Operation(operationId = "getAdminSalesPosts", summary = "관리자 판매글 목록")
    public RsData<PageResponse<AdminSalesPostSummaryResponse>> list(
            @RequestParam(required = false) SalesPostSaleStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        return new RsData<>("200-1", "판매글 목록 조회 성공", salesPostFacade.getAdminSalesPosts(status, keyword, pageable));
    }

    @GetMapping("/counts")
    @Operation(operationId = "getAdminSalesPostCounts", summary = "관리자 판매글 상태별 개수")
    public RsData<AdminSalesPostCountResponse> counts() {
        return new RsData<>("200-1", "판매글 집계 조회 성공", salesPostFacade.getAdminSalesPostCounts());
    }

    @GetMapping("/{postId}")
    @Operation(operationId = "getAdminSalesPostDetail", summary = "관리자 판매글 상세")
    public RsData<AdminSalesPostDetailResponse> detail(@PathVariable Long postId) {
        return new RsData<>("200-1", "판매글 상세 조회 성공", salesPostFacade.getAdminSalesPostDetail(postId));
    }

    @PostMapping("/{postId}/suspend")
    @Operation(operationId = "suspendSalesPost", summary = "판매글 관리자 숨김")
    public RsData<Void> suspend(@AuthenticationPrincipal SecurityUser user,
                                @PathVariable Long postId,
                                @Valid @RequestBody SalesPostSanctionRequest request) {
        salesPostFacade.suspendSalesPostByAdmin(postId, user.memberId(), request.reason());
        return new RsData<>("200-1", "판매글을 숨겼습니다. 진행 중인 주문은 취소·환불됩니다.");
    }

    @PostMapping("/{postId}/resume")
    @Operation(operationId = "resumeSalesPost", summary = "판매글 숨김 해제")
    public RsData<Void> resume(@AuthenticationPrincipal SecurityUser user,
                               @PathVariable Long postId,
                               @Valid @RequestBody SalesPostSanctionRequest request) {
        salesPostFacade.resumeSalesPostByAdmin(postId, user.memberId(), request.reason());
        return new RsData<>("200-1", "판매글 숨김을 해제했습니다.");
    }
}