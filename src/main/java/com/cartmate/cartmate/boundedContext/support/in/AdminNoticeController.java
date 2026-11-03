package com.cartmate.cartmate.boundedContext.support.in.admin;

import com.cartmate.cartmate.boundedContext.support.app.NoticeFacade;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeDetailResponse;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSaveRequest;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSummaryResponse;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 관리자 공지사항 관리. /api/admin/** 는 보안 설정에서 ADMIN만 접근 가능.
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeFacade noticeFacade;

    @GetMapping
    @Operation(operationId = "getAdminNotices", summary = "관리자 공지사항 목록")
    public RsData<PageResponse<NoticeSummaryResponse>> list(
            @RequestParam(required = false) NoticeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new RsData<>("200-1", "공지사항 목록 조회 성공", noticeFacade.search(category, keyword, pageable));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAdminNotice", summary = "관리자 공지사항 상세 (조회수 증가 없음)")
    public RsData<NoticeDetailResponse> detail(@PathVariable Long id) {
        return new RsData<>("200-1", "공지사항 조회 성공", noticeFacade.get(id));
    }

    @PostMapping
    @Operation(operationId = "createNotice", summary = "공지사항 등록")
    public RsData<NoticeDetailResponse> create(@AuthenticationPrincipal SecurityUser user,
                                               @Valid @RequestBody NoticeSaveRequest request) {
        return new RsData<>("201-1", "공지사항을 등록했습니다.", noticeFacade.create(user.memberId(), request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateNotice", summary = "공지사항 수정")
    public RsData<NoticeDetailResponse> update(@PathVariable Long id, @Valid @RequestBody NoticeSaveRequest request) {
        return new RsData<>("200-1", "공지사항을 수정했습니다.", noticeFacade.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteNotice", summary = "공지사항 삭제")
    public RsData<Void> delete(@PathVariable Long id) {
        noticeFacade.delete(id);
        return new RsData<>("200-1", "공지사항을 삭제했습니다.");
    }
}