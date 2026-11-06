package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberIdentity;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberIdentityRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseIdentityIntegrationTest {

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseMemberIdentityRepository abuseMemberIdentityRepository;

    @Test
    @DisplayName("같은 회원의 같은 전화 · 주소는 표기가 달라도 종류마다 한 행만 남고, 원문은 저장하지 않는다")
    void recordsIdentityOncePerType() {
        abuseFacade.recordIdentity(new AbuseIdentityCommand(7L, "010-1111-2222", "06236", "서울 강남구 테헤란로 1", "101호"));
        abuseFacade.recordIdentity(new AbuseIdentityCommand(7L, "01011112222", "06236", "서울강남구 테헤란로1", "101 호"));

        List<AbuseMemberIdentity> identities = abuseMemberIdentityRepository.findByMemberId(7L);
        assertThat(identities).extracting(AbuseMemberIdentity::getIdentityType)
                .containsExactlyInAnyOrder(AbuseIdentityType.PHONE, AbuseIdentityType.ADDRESS);
        assertThat(identities).extracting(AbuseMemberIdentity::getIdentityHash)
                .allSatisfy(hash -> assertThat(hash).hasSize(64).doesNotContain("01011112222"));
    }
}
