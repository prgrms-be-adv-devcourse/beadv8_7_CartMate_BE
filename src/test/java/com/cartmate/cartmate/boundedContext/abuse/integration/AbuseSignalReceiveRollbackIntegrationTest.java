package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseSignalReceiveRollbackIntegrationTest {

    @MockitoBean
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 9_200_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        TestDataCleaner cleaner = new TestDataCleaner(jdbcTemplate);
        cleaner.deleteAbuseRecordsOf(memberIds);
        cleaner.deleteSharedGroupOrdersOfPurchases(purchaseIds);
    }

    @Test
    @DisplayName("신호 접수가 실패하면 기한 만료 취소도 함께 롤백되어 주문이 결제 중으로 남는다")
    void cancelRollsBackWhenSignalReceiveFails() {
        long creator = nextMemberId();
        long unpaid = nextMemberId();
        Long orderId = saveExpiredOrder(creator, unpaid);
        willThrow(new IllegalStateException("abuse_signal 저장 실패"))
                .given(abuseSignalRepository)
                .insertIfAbsent(anyLong(), anyString(), anyString(), any(), any(), anyString());

        assertThatThrownBy(() -> sharedGroupOrderFacade.cancelByDeadline(orderId))
                .isInstanceOf(IllegalStateException.class);

        SharedGroupOrder order = sharedGroupOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(order.getCanceledAt()).isNull();
    }

    private Long saveExpiredOrder(long creator, long unpaid) {
        long purchaseId = base + sequence++;
        purchaseIds.add(purchaseId);
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(5);
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = SharedGroupOrder.create(purchaseId, creator, List.of(creator, unpaid),
                    List.of(new OrderItemLine(2L, 4L, 1L, "상품", null, 1, 30000L)), closedAt);
            order.markStockReserved();
            order.pay(creator, creator, closedAt.plusMinutes(1),
                    DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"));
            return sharedGroupOrderRepository.save(order).getId();
        });
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
