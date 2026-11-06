package com.cartmate.cartmate.boundedContext.memberService.member.in.internal;

import com.cartmate.cartmate.boundedContext.memberService.member.app.SellerQueryAdapter;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.MemberQuery;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 회원 서버 내부 API. 다른 서버(주문 · 배송)만 부른다 — 외부 요청은 Gateway가 막고, 앱은 내부 API 키로 한 번 더 확인한다.
 * 프론트 API 문서·생성 코드에 나오지 않게 숨긴다.
 */
@Hidden
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class MemberInternalController {

    private final MemberQuery memberQuery;
    private final SellerQueryAdapter sellerQueryAdapter;

    // 회원 ID별 닉네임. 없는 회원은 값이 null
    @GetMapping("/nicknames")
    public RsData<Map<Long, String>> findNicknames(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "닉네임 조회", memberQuery.findNicknames(ids));
    }

    // 회원에 연결된 판매자 ID. 판매자가 아니면 data가 null
    @GetMapping("/{memberId}/seller-id")
    public RsData<Long> findSellerId(@PathVariable Long memberId) {
        return new RsData<>("200-1", "판매자 ID 조회", sellerQueryAdapter.findSellerIdByMemberId(memberId).orElse(null));
    }
}
