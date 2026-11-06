package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.GroupPurchaseSalesStopUseCase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.demandPurchase;
import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.memberPurchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CartMateIntegrationTest
class IndividualGroupPurchaseCloseIntegrationTest {

    private static final Long ADMIN = 900_000_001L;
    private static final Long OTHER_ADMIN = 900_000_002L;

    @Autowired
    private IndividualGroupPurchaseFacade facade;

    @Autowired
    private GroupPurchaseSalesStopUseCase salesStopUseCase;

    @Autowired
    private IndividualGroupPurchaseRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("마감 시각이 지난 어드민 공동구매만 시스템 마감 대상이고, 닫으면 수요 키가 비워지며 DEADLINE 종료 이벤트가 남는다")
    void closeByDeadline() {
        Long expired = saveDemand();
        Long notYet = saveDemand();
        Long memberExpired = saveMember();
        expireDeadline(expired);
        expireDeadline(memberExpired);

        List<Long> targets = facade.findDemandIdsToCloseByDeadline(LocalDateTime.now());
        assertThat(targets).contains(expired).doesNotContain(notYet, memberExpired);

        facade.closeByDeadline(expired);
        facade.closeByDeadline(expired);

        Map<String, Object> row = purchaseRow(expired);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("demand_product_key")).isNull();
        assertThat(closedEvents(expired)).singleElement().satisfies(payload ->
                assertThat(payload).contains("\"closeReason\":\"DEADLINE\"", "\"origin\":\"ADMIN_DEMAND\""));
        assertThat(purchaseRow(memberExpired).get("status")).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("다른 어드민도 어드민 공동구매를 마감하고 현황판을 본다. 회원 공동구매는 어드민 권한으로 다룰 수 없다")
    void adminCloseAndDashboard() {
        Long demand = saveDemand();
        Long member = saveMember();

        assertThat(facade.getDashboardForAdmin(demand).orderCount()).isZero();
        facade.closeByAdmin(OTHER_ADMIN, demand);

        assertThat(purchaseRow(demand).get("status")).isEqualTo("CLOSED");
        assertThat(closedEvents(demand)).singleElement().satisfies(payload ->
                assertThat(payload).contains("\"closeReason\":\"ADMIN\""));
        assertThatThrownBy(() -> facade.closeByAdmin(OTHER_ADMIN, member))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_ADMIN_DEMAND_ONLY.getMsg());
        assertThatThrownBy(() -> facade.getDashboardForAdmin(member))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_ADMIN_DEMAND_ONLY.getMsg());
        assertThat(purchaseRow(member).get("status")).isEqualTo("OPEN");
        assertThat(closedEvents(member)).isEmpty();
    }

    @Test
    @DisplayName("개설자 마감과 판매 중지로 닫힐 때도 종료 이벤트가 남고, 판매 중지는 수요 키를 비운다")
    void creatorAndSalesStopClose() {
        Long byCreator = saveDemand();
        Long bySalesStop = saveDemand();

        facade.close(ADMIN, byCreator);
        Long productId = ((Number) purchaseRow(bySalesStop).get("demand_product_key")).longValue();
        salesStopUseCase.removeFromIndividual(bySalesStop, List.of(productId));

        assertThat(closedEvents(byCreator)).singleElement().satisfies(payload ->
                assertThat(payload).contains("\"closeReason\":\"CREATOR\""));
        Map<String, Object> row = purchaseRow(bySalesStop);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("demand_product_key")).isNull();
        assertThat(closedEvents(bySalesStop)).singleElement().satisfies(payload ->
                assertThat(payload).contains("\"closeReason\":\"SALES_STOPPED\""));
    }

    private Long saveDemand() {
        long productId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        LocalDateTime now = LocalDateTime.now();
        IndividualGroupPurchase purchase = demandPurchase(ADMIN, inviteCode(), productId, now.plusDays(3), now);
        return repository.saveAndFlush(purchase).getId();
    }

    private Long saveMember() {
        long productId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        IndividualGroupPurchase purchase = memberPurchase(900_000_003L, inviteCode(), List.of(productId),
                LocalDateTime.now());
        return repository.saveAndFlush(purchase).getId();
    }

    private void expireDeadline(Long id) {
        jdbcTemplate.update("UPDATE individual_group_purchase SET deadline_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), id);
    }

    private Map<String, Object> purchaseRow(Long id) {
        return jdbcTemplate.queryForMap("SELECT status, demand_product_key FROM individual_group_purchase WHERE id = ?", id);
    }

    private List<String> closedEvents(Long id) {
        return jdbcTemplate.queryForList("SELECT payload FROM outbox_event WHERE aggregate_type = 'IndividualGroupPurchase' "
                + "AND aggregate_id = ? AND event_type = 'IndividualGroupPurchaseClosedEvent' AND topic = ?",
                String.class, String.valueOf(id), "cartmate.cart.individual-group-purchase-closed");
    }

    private static String inviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
