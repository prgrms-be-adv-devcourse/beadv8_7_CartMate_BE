package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.abuse.support.AbuseTestDataCleaner;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SharedGroupPurchaseRestrictionTest {

    @Autowired
    private SharedGroupPurchaseFacade facade;

    @Autowired
    private SharedGroupPurchaseRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    private final long base = 9_800_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        new AbuseTestDataCleaner(jdbcTemplate).deleteRecordsOf(memberIds);
        transactionTemplate.executeWithoutResult(status -> createdIds.forEach(repository::deleteById));
    }

    @Test
    @DisplayName("제한 중인 회원은 공동구매-공동물품을 만들 수 없고 응답에 제한 종료 시각이 들어간다")
    void restrictedMemberCannotCreate() {
        long member = nextMemberId();
        restrict(member, LocalDateTime.of(2099, 1, 2, 3, 4));

        assertThatThrownBy(() -> facade.create(member, new SharedGroupPurchaseCreateRequest("제한", 3)))
                .isInstanceOfSatisfying(ServiceException.class, e -> {
                    assertThat(e.getResultCode()).isEqualTo(CartErrorCode.CART_MEMBER_RESTRICTED.resultCode());
                    assertThat(e.getMsg()).endsWith("제한 종료: 2099-01-02 03:04");
                });
    }

    @Test
    @DisplayName("제한 중인 회원은 자기 공동구매를 복제할 수 없다")
    void restrictedMemberCannotDuplicate() {
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse created = create(member);
        restrict(member, LocalDateTime.now().plusHours(1));

        assertThatThrownBy(() -> facade.duplicate(member, created.id()))
                .isInstanceOfSatisfying(ServiceException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(CartErrorCode.CART_MEMBER_RESTRICTED.resultCode()));
    }

    @Test
    @DisplayName("제한 중인 회원은 새 공동구매에 참여할 수 없지만 이미 참여한 공동구매는 그대로다")
    void restrictedMemberCannotJoinNewButKeepsExisting() {
        long creator = nextMemberId();
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse joined = create(creator);
        SharedGroupPurchaseCreateResponse other = create(creator);
        facade.join(member, joined.inviteCode());
        restrict(member, LocalDateTime.now().plusHours(1));

        assertThat(facade.join(member, joined.inviteCode())).isEqualTo(joined.id());
        assertThatThrownBy(() -> facade.join(member, other.inviteCode()))
                .isInstanceOfSatisfying(ServiceException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(CartErrorCode.CART_MEMBER_RESTRICTED.resultCode()));
    }

    @Test
    @DisplayName("제한이 끝난 회원은 다시 만들고 참여할 수 있다")
    void expiredRestrictionAllows() {
        long creator = nextMemberId();
        long member = nextMemberId();
        restrict(creator, LocalDateTime.now().minusMinutes(1));
        restrict(member, LocalDateTime.now().minusMinutes(1));

        SharedGroupPurchaseCreateResponse created = create(creator);

        assertThat(facade.join(member, created.inviteCode())).isEqualTo(created.id());
    }

    private SharedGroupPurchaseCreateResponse create(long creator) {
        SharedGroupPurchaseCreateResponse created = facade.create(creator, new SharedGroupPurchaseCreateRequest("제한 테스트", 5));
        createdIds.add(created.id());
        return created;
    }

    private void restrict(long memberId, LocalDateTime restrictedUntil) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO abuse_member_state (member_id, current_level, restricted_until, created_at, updated_at)
                VALUES (?, 'RESTRICTED', ?, ?, ?)
                """, memberId, restrictedUntil, now, now);
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
