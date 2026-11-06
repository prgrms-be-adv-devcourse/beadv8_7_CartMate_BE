package com.cartmate.cartmate.boundedContext.orderService.cart.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공동구매 내부 API. 회원 서버만 부른다(마이페이지 활동 · 탈퇴 가능 여부, MSA-6).
 * 응답은 화면용 DTO가 아니라 회원 서버와 맞춘 계약 레코드다 — 화면 DTO를 바꿔도 회원 서버가 깨지지 않게.
 */
@Hidden
@RestController
@RequestMapping("/internal/group-purchases")
@RequiredArgsConstructor
public class GroupPurchaseInternalController {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    // 회원이 참여한 공동물품 공동구매
    @GetMapping("/shared")
    public RsData<List<SharedPurchaseItem>> getShared(@RequestParam Long memberId) {
        List<SharedPurchaseItem> items = sharedGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new SharedPurchaseItem(p.id(), p.title(), p.status().name(),
                        p.participantCount(), p.maxParticipants(), p.editDeadlineAt()))
                .toList();
        return new RsData<>("200-1", "참여한 공동물품 공동구매", items);
    }

    // 회원이 연 개인물품 공동구매
    @GetMapping("/individual")
    public RsData<List<IndividualPurchaseItem>> getIndividual(@RequestParam Long memberId) {
        List<IndividualPurchaseItem> items = individualGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new IndividualPurchaseItem(p.id(), p.title(), p.open(), p.productCount(), p.deadlineAt()))
                .toList();
        return new RsData<>("200-1", "개설한 개인물품 공동구매", items);
    }

    // 탈퇴 판단용: 모집 중인 공동구매 수(참여한 공동물품 + 개설한 개인물품)
    @GetMapping("/active-count")
    public RsData<Long> countActive(@RequestParam Long memberId) {
        long count = sharedGroupPurchaseFacade.countActiveOfMember(memberId)
                + individualGroupPurchaseFacade.countOpenOfCreator(memberId);
        return new RsData<>("200-1", "모집 중인 공동구매 수", count);
    }

    public record SharedPurchaseItem(Long id, String title, String status, int participantCount, int maxParticipants,
                                     LocalDateTime editDeadlineAt) {
    }

    public record IndividualPurchaseItem(Long id, String title, boolean open, int productCount,
                                         LocalDateTime deadlineAt) {
    }
}
