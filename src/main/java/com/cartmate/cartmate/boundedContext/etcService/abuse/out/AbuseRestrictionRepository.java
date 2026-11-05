package com.cartmate.cartmate.boundedContext.etcService.abuse.out;

import com.cartmate.cartmate.boundedContext.etcService.abuse.domain.AbuseRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AbuseRestrictionRepository extends JpaRepository<AbuseRestriction, Long> {

    List<AbuseRestriction> findByMemberIdOrderByStartedAtAsc(Long memberId);
}
