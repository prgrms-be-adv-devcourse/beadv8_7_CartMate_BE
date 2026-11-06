package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAccountLinkRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseAccountLinkIntegrationTest {

    private static final String PHONE = "010-5555-6666";

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseAccountLinkRepository abuseAccountLinkRepository;

    @Test
    @DisplayName("같은 전화를 쓰는 두 계정이 서로 다른 공동구매 2곳에서 함께 신호를 내면 연결된다")
    void linksOnSharedIdentityAndTwoSharedPurchases() {
        identity(1L, PHONE, "주소 A");
        identity(2L, PHONE, "주소 B");

        expired(1L, 100L);
        expired(2L, 100L);
        expired(1L, 200L);
        expired(2L, 200L);

        List<AbuseAccountLink> links = abuseAccountLinkRepository.findByMember(1L);
        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.otherThan(1L)).isEqualTo(2L);
            assertThat(link.getIdentityType()).isEqualTo(AbuseIdentityType.PHONE);
            assertThat(link.getSharedContextCount()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("함께한 공동구매가 1곳뿐이면 같은 전화여도 연결하지 않는다")
    void doesNotLinkOnSingleSharedPurchase() {
        identity(1L, PHONE, "주소 A");
        identity(2L, PHONE, "주소 B");

        expired(1L, 100L);
        expired(2L, 100L);
        expired(1L, 200L);
        expired(2L, 300L);

        assertThat(abuseAccountLinkRepository.findByMember(1L)).isEmpty();
    }

    @Test
    @DisplayName("함께 어뷰징했어도 전화 · 주소가 겹치지 않으면 연결하지 않는다")
    void doesNotLinkWithoutSharedIdentity() {
        identity(1L, "010-1111-1111", "주소 A");
        identity(2L, "010-2222-2222", "주소 B");

        expired(1L, 100L);
        expired(2L, 100L);
        expired(1L, 200L);
        expired(2L, 200L);

        assertThat(abuseAccountLinkRepository.findByMember(1L)).isEmpty();
    }

    @Test
    @DisplayName("같은 주소를 10명까지 쓰면 연결 근거가 된다")
    void linksOnAddressSharedByTenMembers() {
        for (long member = 1L; member <= 10L; member++) {
            abuseFacade.recordIdentity(new AbuseIdentityCommand(member, "010-9" + member + "00-0000", "00000",
                    "택배 보관함", "1층"));
        }

        expired(1L, 100L);
        expired(2L, 100L);
        expired(1L, 200L);
        expired(2L, 200L);

        assertThat(abuseAccountLinkRepository.findByMember(1L)).singleElement()
                .satisfies(link -> assertThat(link.getIdentityType()).isEqualTo(AbuseIdentityType.ADDRESS));
    }

    @Test
    @DisplayName("11명 넘게 쓰는 공용 주소는 연결 근거로 쓰지 않는다")
    void ignoresIdentitySharedByTooManyMembers() {
        for (long member = 1L; member <= 11L; member++) {
            abuseFacade.recordIdentity(new AbuseIdentityCommand(member, "010-9" + member + "00-0000", "00000",
                    "택배 보관함", "1층"));
        }

        expired(1L, 100L);
        expired(2L, 100L);
        expired(1L, 200L);
        expired(2L, 200L);

        assertThat(abuseAccountLinkRepository.findByMember(1L)).isEmpty();
    }

    private void identity(long memberId, String phone, String address) {
        abuseFacade.recordIdentity(new AbuseIdentityCommand(memberId, phone, "00000", address, ""));
    }

    private void expired(long memberId, long sharedGroupPurchaseId) {
        abuseFacade.receive(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "order-" + sharedGroupPurchaseId, LocalDateTime.now(),
                AbuseSignalCommand.sharedGroupPurchaseContext(sharedGroupPurchaseId)));
        abuseFacade.confirmLinks(memberId);
        abuseFacade.judgePending(memberId);
    }
}
