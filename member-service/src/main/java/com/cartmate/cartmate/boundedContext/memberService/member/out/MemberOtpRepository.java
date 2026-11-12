package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberOtpRepository extends JpaRepository<MemberOtp, Long> {

    Optional<MemberOtp> findByMemberId(Long memberId);

    // 성공한 30초 구간을 기록한다. 이미 그 구간(또는 이후)을 썼으면 0 → 같은 코드 재사용·동시 사용을 막는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberOtp o set o.lastUsedStep = :step where o.memberId = :memberId and o.enabledAt is not null and o.lastUsedStep < :step")
    int markUsed(@Param("memberId") Long memberId, @Param("step") long step);
}
