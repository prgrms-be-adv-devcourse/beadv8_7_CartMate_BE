package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.GroupPurchaseSalesStopUseCase;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class SharedGroupPurchaseEventPublishTest {

    @Autowired
    private SharedGroupPurchaseFacade facade;

    @Autowired
    private GroupPurchaseSalesStopUseCase salesStopUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 9_850_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("참여하면 참여 이벤트에 그 시점 진행 중인 참여 수가 실리고, 이미 참여한 회원의 재참여는 이벤트를 내지 않는다")
    void publishesJoinedWithActiveParticipationCount() {
        long creator = nextMemberId();
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse first = create(creator);
        SharedGroupPurchaseCreateResponse second = create(creator);

        facade.join(member, first.inviteCode());
        facade.join(member, second.inviteCode());
        facade.join(member, second.inviteCode());

        assertThat(payloads("SharedGroupPurchaseMemberJoinedEvent", "cartmate.cart.shared-group-purchase-member-joined", first.id()))
                .singleElement().asString()
                .contains("\"memberId\":" + member, "\"activeParticipationCount\":1");
        assertThat(payloads("SharedGroupPurchaseMemberJoinedEvent", "cartmate.cart.shared-group-purchase-member-joined", second.id()))
                .singleElement().asString()
                .contains("\"memberId\":" + member, "\"activeParticipationCount\":2");
    }

    @Test
    @DisplayName("스스로 이탈하면 이탈 이벤트에 참여 시각과 이탈 시각이 실리고, 강퇴는 이탈 이벤트를 내지 않는다")
    void publishesLeftOnlyForSelfLeave() {
        long creator = nextMemberId();
        long leaver = nextMemberId();
        long kicked = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        facade.join(leaver, purchase.inviteCode());
        facade.join(kicked, purchase.inviteCode());

        facade.leave(leaver, purchase.id());
        facade.kick(creator, purchase.id(), kicked);

        assertThat(payloads("SharedGroupPurchaseMemberLeftEvent", "cartmate.cart.shared-group-purchase-member-left", purchase.id()))
                .singleElement().asString()
                .contains("\"memberId\":" + leaver, "\"joinedAt\":", "\"leftAt\":")
                .doesNotContain("\"memberId\":" + kicked);
    }

    @Test
    @DisplayName("방장이 취소하면 방장 · 참여자 · 사유가 실린 취소 이벤트를 낸다")
    void publishesCanceledByCreator() {
        long creator = nextMemberId();
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        facade.join(member, purchase.inviteCode());

        facade.cancel(creator, purchase.id());

        assertThat(payloads("SharedGroupPurchaseCanceledEvent", "cartmate.cart.shared-group-purchase-canceled", purchase.id()))
                .singleElement().asString()
                .contains("\"creatorMemberId\":" + creator, "\"cancelReason\":\"CREATOR_CANCELED\"",
                        String.valueOf(member));
    }

    @Test
    @DisplayName("편집 마감 때 담긴 상품이 없어 취소되면 마감 사유로 취소 이벤트를 내고, 상품이 있으면 내지 않는다")
    void publishesCanceledAtDeadlineOnlyWhenEmpty() {
        long creator = nextMemberId();
        SharedGroupPurchaseCreateResponse empty = create(creator);
        SharedGroupPurchaseCreateResponse withItem = create(creator);
        insertItem(withItem.id(), creator, 1L);
        passEditDeadline(empty.id());
        passEditDeadline(withItem.id());

        facade.closeByDeadline(empty.id());
        facade.closeByDeadline(withItem.id());

        assertThat(canceledPayloads(empty.id())).singleElement().asString()
                .contains("\"cancelReason\":\"NO_ITEMS_AT_DEADLINE\"");
        assertThat(canceledPayloads(withItem.id())).isEmpty();
    }

    @Test
    @DisplayName("마감 보류가 끝났을 때 남은 상품이 없어 취소되면 보류 사유로 취소 이벤트를 낸다")
    void publishesCanceledAfterFrozenTimeout() {
        long creator = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        jdbcTemplate.update("UPDATE shared_group_purchase SET status = 'FROZEN', frozen_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(2), purchase.id());

        facade.closeByFrozenTimeout(purchase.id());

        assertThat(canceledPayloads(purchase.id())).singleElement().asString()
                .contains("\"cancelReason\":\"NO_ITEMS_AFTER_FROZEN\"");
    }

    @Test
    @DisplayName("판매 중지로 마지막 상품이 빠져 취소되면 판매 중지 사유로 취소 이벤트를 낸다")
    void publishesCanceledBySalesStopped() {
        long creator = nextMemberId();
        long productId = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        insertItem(purchase.id(), creator, productId);

        salesStopUseCase.removeFromShared(purchase.id(), List.of(productId));

        assertThat(canceledPayloads(purchase.id())).singleElement().asString()
                .contains("\"cancelReason\":\"SALES_STOPPED\"");
    }

    private SharedGroupPurchaseCreateResponse create(long creator) {
        return facade.create(creator, new SharedGroupPurchaseCreateRequest("이벤트 테스트", 5));
    }

    private List<String> canceledPayloads(Long sharedGroupPurchaseId) {
        return payloads("SharedGroupPurchaseCanceledEvent", "cartmate.cart.shared-group-purchase-canceled", sharedGroupPurchaseId);
    }

    private void passEditDeadline(Long sharedGroupPurchaseId) {
        jdbcTemplate.update("UPDATE shared_group_purchase SET edit_deadline_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), sharedGroupPurchaseId);
    }

    private void insertItem(Long sharedGroupPurchaseId, long memberId, Long productId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO shared_group_purchase_item
                    (shared_group_purchase_id, product_id, inventory_id, quantity, updated_member_id, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, 0, ?, ?)
                """, sharedGroupPurchaseId, productId, productId, memberId, now, now);
    }

    private List<String> payloads(String eventType, String topic, Long sharedGroupPurchaseId) {
        return jdbcTemplate.queryForList("SELECT payload FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' "
                        + "AND aggregate_id = ? AND event_type = ? AND topic = ?",
                String.class, String.valueOf(sharedGroupPurchaseId), eventType, topic);
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
