package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberBackupCodeRepository extends JpaRepository<MemberBackupCode, Long> {

    // 백업 코드 사용: 지운 행이 있을 때만 성공 (같은 코드를 동시에 두 번 써도 한 번만)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MemberBackupCode c where c.memberId = :memberId and c.codeHash = :codeHash")
    int use(@Param("memberId") Long memberId, @Param("codeHash") String codeHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MemberBackupCode c where c.memberId = :memberId")
    int deleteAllOf(@Param("memberId") Long memberId);

    long countByMemberId(Long memberId);
}
