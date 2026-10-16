package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.MemberSuspension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberSuspensionRepository extends JpaRepository<MemberSuspension, Long> {

    List<MemberSuspension> findByMemberIdOrderBySuspendedAtDesc(Long memberId);

    Optional<MemberSuspension> findFirstByMemberIdAndReleasedAtIsNullOrderBySuspendedAtDesc(Long memberId);

    List<MemberSuspension> findByReleasedAtIsNullAndSuspendedUntilBefore(LocalDateTime now);
}