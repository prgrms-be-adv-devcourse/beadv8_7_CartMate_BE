package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.dto.AdminMemberResponse;
import com.cartmate.cartmate.boundedContext.member.dto.AdminMemberSearchRequest;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.MemberSpecifications;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMemberSearchUseCase {
    private static final int MAX_PAGE_SIZE = 100;

    private final MemberRepository memberRepository;

    public PageResponse<AdminMemberResponse> search(AdminMemberSearchRequest condition, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return PageResponse.of(
                memberRepository.findAll(MemberSpecifications.adminSearch(condition), pageable)
                        .map(AdminMemberResponse::from)
        );
    }
}