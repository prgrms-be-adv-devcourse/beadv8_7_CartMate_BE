package com.cartmate.cartmate.boundedContext.support.in;

import com.cartmate.cartmate.boundedContext.support.app.NoticeFacade;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeDetailResponse;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSummaryResponse;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

// 사용자 공지사항. 로그인 없이 볼 수 있다.
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeFacade noticeFacade;

    @GetMapping
    @Operation(operationId = "getNotices", summary = "공지사항 목록 (고정 먼저, 최신순)")
    public RsData<PageResponse<NoticeSummaryResponse>> list(
            @RequestParam(required = false) NoticeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return new RsData<>("200-1", "공지사항 목록 조회 성공", noticeFacade.search(category, keyword, pageable));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getNotice", summary = "공지사항 상세 (조회수 증가)")
    public RsData<NoticeDetailResponse> detail(@PathVariable Long id) {
        return new RsData<>("200-1", "공지사항 조회 성공", noticeFacade.view(id));
    }
}