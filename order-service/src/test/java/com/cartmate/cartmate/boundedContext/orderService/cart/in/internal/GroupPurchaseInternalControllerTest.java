package com.cartmate.cartmate.boundedContext.orderService.cart.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.memberPurchase;
import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.sharedPurchase;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CartMateIntegrationTest
class GroupPurchaseInternalControllerTest {

    private static final long CREATOR = 800_000_001L;
    private static final long MEMBER = 800_000_002L;
    private static final long OTHER_CREATOR = 800_000_003L;
    private static final long OTHER_MEMBER = 800_000_004L;
    private static final long INVENTORY_ID = 11L;
    private static final LocalDateTime PAST = LocalDateTime.of(2026, 3, 2, 10, 0);
    private static final LocalDateTime AFTER_EDIT_DEADLINE = PAST.plusDays(3);
    private static final LocalDateTime FUTURE = LocalDateTime.of(2099, 1, 1, 10, 0);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private IndividualGroupPurchaseRepository individualGroupPurchaseRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("공동물품 공동구매 목록은 참가자로 들어간 것만 개설 여부·상태와 상관없이 최신순으로 담고, 각 필드의 이름과 타입이 계약대로다")
    void shared() throws Exception {
        Long joined = sharedGroupPurchaseRepository.save(sharedPurchase(CREATOR, MEMBER, "shared000001", PAST)).getId();
        SharedGroupPurchase createdThenCanceled = sharedPurchase(MEMBER, CREATOR, "shared000002", PAST);
        createdThenCanceled.cancel(MEMBER, PAST);
        Long canceled = sharedGroupPurchaseRepository.save(createdThenCanceled).getId();
        sharedGroupPurchaseRepository.save(sharedPurchase(OTHER_CREATOR, OTHER_MEMBER, "shared000003", PAST));

        call("/internal/group-purchases/shared", MEMBER)
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", contains(num(canceled), num(joined))))
                .andExpect(jsonPath("$.data[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data[1].title").value("FC 훈련용품"))
                .andExpect(jsonPath("$.data[1].status").value("OPEN"))
                .andExpect(jsonPath("$.data[1].participantCount", equalTo(2)))
                .andExpect(jsonPath("$.data[1].maxParticipants", equalTo(5)))
                .andExpect(jsonPath("$.data[1].editDeadlineAt").value("2026-03-04T10:00:00"));
    }

    @Test
    @DisplayName("개인물품 공동구매 목록은 그 회원이 개설한 것만 최신순으로 담고, open은 모집 상태이면서 마감 시각 전일 때만 true다")
    void individual() throws Exception {
        Long openPurchase = individualGroupPurchaseRepository.save(
                memberPurchase(MEMBER, "indiv0000001", List.of(1L, 2L, 2L), FUTURE)).getId();
        Long expired = individualGroupPurchaseRepository.save(
                memberPurchase(MEMBER, "indiv0000002", List.of(1L), PAST)).getId();
        IndividualGroupPurchase closedPurchase = memberPurchase(MEMBER, "indiv0000003", List.of(1L), FUTURE);
        closedPurchase.close(MEMBER, FUTURE);
        Long closed = individualGroupPurchaseRepository.save(closedPurchase).getId();
        individualGroupPurchaseRepository.save(memberPurchase(OTHER_CREATOR, "indiv0000004", List.of(1L), FUTURE));

        call("/internal/group-purchases/individual", MEMBER)
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].id", contains(num(closed), num(expired), num(openPurchase))))
                .andExpect(jsonPath("$.data[*].open", contains(false, false, true)))
                .andExpect(jsonPath("$.data[2].title").value("OO FC 2026 유니폼"))
                .andExpect(jsonPath("$.data[2].productCount", equalTo(2)))
                .andExpect(jsonPath("$.data[2].deadlineAt").value("2099-01-03T10:00:00"));
    }

    @Test
    @DisplayName("모집 중인 공동구매 수는 참가한 공동물품 공동구매 중 모집 중·보류·마감(주문 생성 전)과 개설한 개인물품 공동구매 중 모집 상태이면서 판매 기한 전인 것을 더한 값이다")
    void activeCount() throws Exception {
        sharedGroupPurchaseRepository.save(sharedPurchase(CREATOR, MEMBER, "shared000001", PAST));
        sharedGroupPurchaseRepository.save(frozen("shared000002"));
        sharedGroupPurchaseRepository.save(closed("shared000003"));
        SharedGroupPurchase orderCreated = closed("shared000004");
        orderCreated.markOrderCreated();
        sharedGroupPurchaseRepository.save(orderCreated);
        SharedGroupPurchase canceled = sharedPurchase(CREATOR, MEMBER, "shared000005", PAST);
        canceled.cancel(CREATOR, PAST);
        sharedGroupPurchaseRepository.save(canceled);
        sharedGroupPurchaseRepository.save(sharedPurchase(OTHER_CREATOR, OTHER_MEMBER, "shared000006", PAST));

        individualGroupPurchaseRepository.save(memberPurchase(MEMBER, "indiv0000001", List.of(1L), FUTURE));
        IndividualGroupPurchase closedIndividual = memberPurchase(MEMBER, "indiv0000002", List.of(1L), FUTURE);
        closedIndividual.close(MEMBER, FUTURE);
        individualGroupPurchaseRepository.save(closedIndividual);
        individualGroupPurchaseRepository.save(memberPurchase(OTHER_CREATOR, "indiv0000003", List.of(1L), FUTURE));
        individualGroupPurchaseRepository.save(memberPurchase(MEMBER, "indiv0000004", List.of(1L), PAST));

        call("/internal/group-purchases/active-count", MEMBER)
                .andExpect(jsonPath("$.data", equalTo(4)));
    }

    @Test
    @DisplayName("공동구매가 없는 회원이면 목록 응답의 data는 null이 아닌 빈 배열이다")
    void emptyListsAreArrays() throws Exception {
        for (String path : List.of("/internal/group-purchases/shared", "/internal/group-purchases/individual")) {
            call(path, MEMBER).andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    private ResultActions call(String path, long memberId) throws Exception {
        return mvc.perform(get(path).param("memberId", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
    }

    private SharedGroupPurchase frozen(String inviteCode) {
        SharedGroupPurchase purchase = withItem(inviteCode);
        purchase.closeByDeadline(Set.of(INVENTORY_ID), AFTER_EDIT_DEADLINE);
        return purchase;
    }

    private SharedGroupPurchase closed(String inviteCode) {
        SharedGroupPurchase purchase = withItem(inviteCode);
        purchase.closeByDeadline(Set.of(), AFTER_EDIT_DEADLINE);
        return purchase;
    }

    private SharedGroupPurchase withItem(String inviteCode) {
        SharedGroupPurchase purchase = sharedPurchase(CREATOR, MEMBER, inviteCode, PAST);
        purchase.addItem(MEMBER, 1L, INVENTORY_ID, 1, PAST);
        return purchase;
    }

    private static int num(Long id) {
        return Math.toIntExact(id);
    }
}
