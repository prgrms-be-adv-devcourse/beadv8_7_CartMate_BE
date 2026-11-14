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

@Hidden
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class MemberInternalController {

    private final MemberQuery memberQuery;
    private final SellerQueryAdapter sellerQueryAdapter;

    @GetMapping("/nicknames")
    public RsData<Map<Long, String>> findNicknames(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "닉네임 조회", memberQuery.findNicknames(ids));
    }

    @GetMapping("/{memberId}/seller-id")
    public RsData<Long> findSellerId(@PathVariable Long memberId) {
        return new RsData<>("200-1", "판매자 ID 조회", sellerQueryAdapter.findSellerIdByMemberId(memberId).orElse(null));
    }
}
