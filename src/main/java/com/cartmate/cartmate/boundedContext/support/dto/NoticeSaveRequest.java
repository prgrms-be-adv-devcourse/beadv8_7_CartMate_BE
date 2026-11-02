package com.cartmate.cartmate.boundedContext.support.dto;

import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 공지 등록·수정 요청
public record NoticeSaveRequest(
        @NotNull(message = "구분을 선택해주세요.")
        NoticeCategory category,

        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 200, message = "제목은 200자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "내용을 입력해주세요.")
        @Size(max = 20000, message = "내용은 20,000자 이하로 입력해주세요.")
        String content,

        boolean pinned
) {
}