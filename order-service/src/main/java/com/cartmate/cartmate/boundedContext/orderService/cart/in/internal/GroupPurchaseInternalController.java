package com.cartmate.cartmate.boundedContext.orderService.cart.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.cart.dto.SharedGroupPurchaseActivity;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseActivity;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/group-purchases")
@RequiredArgsConstructor
public class GroupPurchaseInternalController {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @GetMapping("/shared")
    public RsData<List<SharedGroupPurchaseActivity>> getShared(@RequestParam Long memberId) {
        List<SharedGroupPurchaseActivity> items = sharedGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new SharedGroupPurchaseActivity(p.id(), p.title(), p.status(),
                        p.participantCount(), p.maxParticipants(), p.editDeadlineAt()))
                .toList();
        return new RsData<>("200-1", "참여한 공동물품 공동구매", items);
    }

    @GetMapping("/individual")
    public RsData<List<IndividualGroupPurchaseActivity>> getIndividual(@RequestParam Long memberId) {
        List<IndividualGroupPurchaseActivity> items = individualGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new IndividualGroupPurchaseActivity(p.id(), p.title(), p.open(), p.productCount(), p.deadlineAt()))
                .toList();
        return new RsData<>("200-1", "개설한 개인물품 공동구매", items);
    }

    @GetMapping("/active-count")
    public RsData<Long> countActive(@RequestParam Long memberId) {
        long count = sharedGroupPurchaseFacade.countActiveOfMember(memberId)
                + individualGroupPurchaseFacade.countOpenOfCreator(memberId);
        return new RsData<>("200-1", "모집 중인 공동구매 수", count);
    }
}
