package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface  MemberRepository extends JpaRepository<Member, Long>,
        JpaSpecificationExecutor<Member> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
