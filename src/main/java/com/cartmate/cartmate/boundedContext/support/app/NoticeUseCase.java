package com.cartmate.cartmate.boundedContext.support.app;

import com.cartmate.cartmate.boundedContext.support.domain.Notice;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeDetailResponse;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSaveRequest;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSummaryResponse;
import com.cartmate.cartmate.boundedContext.support.exception.SupportErrorCode;
import com.cartmate.cartmate.boundedContext.support.out.NoticeRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class NoticeUseCase {

    private final NoticeRepository noticeRepository;

    PageResponse<NoticeSummaryResponse> search(NoticeCategory category, String keyword, Pageable pageable) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return PageResponse.of(noticeRepository.search(category, trimmed, pageable).map(NoticeSummaryResponse::from));
    }

    NoticeDetailResponse get(Long id) {
        return NoticeDetailResponse.from(find(id));
    }

    // 사용자 상세 조회: 조회수를 올린 뒤 최신 값으로 돌려준다
    NoticeDetailResponse view(Long id) {
        if (noticeRepository.increaseViewCount(id) == 0) {
            throw new ServiceException(SupportErrorCode.NOTICE_NOT_FOUND);
        }
        return NoticeDetailResponse.from(find(id));
    }

    NoticeDetailResponse create(Long adminId, NoticeSaveRequest request) {
        Notice notice = noticeRepository.save(new Notice(
                request.category(), request.title().trim(), request.content(), request.pinned(), adminId));
        return NoticeDetailResponse.from(notice);
    }

    NoticeDetailResponse update(Long id, NoticeSaveRequest request) {
        Notice notice = find(id);
        notice.update(request.category(), request.title().trim(), request.content(), request.pinned());
        return NoticeDetailResponse.from(notice);
    }

    void delete(Long id) {
        noticeRepository.delete(find(id));
    }

    private Notice find(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new ServiceException(SupportErrorCode.NOTICE_NOT_FOUND));
    }
}