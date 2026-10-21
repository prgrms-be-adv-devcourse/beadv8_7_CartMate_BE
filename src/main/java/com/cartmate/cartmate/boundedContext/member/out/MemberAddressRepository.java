package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    Optional<MemberAddress> findFirstByMemberIdAndDefaultAddressTrue(Long memberId);

    // 기본 배송지 먼저, 그다음 최근 등록 순
    List<MemberAddress> findAllByMemberIdOrderByDefaultAddressDescIdDesc(Long memberId);

    long countByMemberId(Long memberId);
}