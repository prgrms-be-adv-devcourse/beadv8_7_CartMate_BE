package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface  MemberRepository extends JpaRepository<Member, Long>,
        JpaSpecificationExecutor<Member> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    @Query("select m.id from Member m where m.status = :status")
    List<Long> findIdsByStatus(@Param("status") MemberStatus status);

    long countByRole(MemberRole role);

    long countByStatus(MemberStatus status);

    long countByCreateDateGreaterThanEqual(LocalDateTime from);

    List<Member> findTop5ByOrderByIdDesc();
}
