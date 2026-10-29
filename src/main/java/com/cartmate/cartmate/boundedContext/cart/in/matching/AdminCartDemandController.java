package com.cartmate.cartmate.boundedContext.cart.in.matching;

import com.cartmate.cartmate.boundedContext.cart.app.matching.CartMatchingFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandCandidateResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/carts/demands")
@RequiredArgsConstructor
public class AdminCartDemandController {

    private final CartMatchingFacade cartMatchingFacade;

    @GetMapping
    public RsData<List<DemandCandidateResponse>> getCandidates(
            @RequestParam(defaultValue = "5") int minMembers,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new RsData<>("200-1", "공동구매 후보를 조회했습니다.",
                cartMatchingFacade.getDemandCandidates(minMembers, days, size));
    }
}
