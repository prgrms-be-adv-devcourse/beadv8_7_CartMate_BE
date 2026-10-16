package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AdminMemberSearchRequest(
        String searchType,       // email, id, nickname
        String keyword,
        MemberStatus status,     // null이면 전체
        MemberRole role,         // null이면 전체
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedTo
) {
}