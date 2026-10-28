package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseJudgment;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbuseJudgmentRepository extends JpaRepository<AbuseJudgment, Long> {

    boolean existsByMemberIdAndSignalTypeAndSourceKey(Long memberId, AbuseSignalType signalType, String sourceKey);
}
