package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AbuseRestrictionRepository extends JpaRepository<AbuseRestriction, Long> {

    Optional<AbuseRestriction> findFirstByMemberIdAndReleasedAtIsNullAndEndsAtAfterOrderByEndsAtDesc(
            Long memberId, LocalDateTime now);
}
