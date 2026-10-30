package com.cartmate.cartmate.boundedContext.trust.in;

import com.cartmate.cartmate.boundedContext.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.trust.dto.SellerTrustResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerTrustController {

    private final TrustFacade trustFacade;

    // 판매자의 신뢰도 점수와 등급을 조회한다. 구매자가 판매자를 비교할 수 있도록 로그인 없이 공개한다.
    @GetMapping("/{sellerId}/trust")
    public RsData<SellerTrustResponse> getSellerTrust(@PathVariable Long sellerId) {
        return new RsData<>("200-1", "판매자 신뢰도 조회 성공", trustFacade.getSellerTrust(sellerId));
    }
}
