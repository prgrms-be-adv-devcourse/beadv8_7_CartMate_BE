package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    // 기기 관리 화면: 아직 쓸 수 있는 기기만
    @Query("select d from TrustedDevice d where d.memberId = :memberId and d.expiresAt > :now order by d.lastUsedAt desc")
    List<TrustedDevice> findUsable(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    // 로그인 때 확인과 토큰 교체를 한 번에: 이 회원의 만료 안 된 기기이고 해시가 그대로일 때만 바뀐다 (1이면 신뢰 기기).
    // 같은 토큰으로 동시에 두 번 와도 한 번만 성공한다. 로그인은 트랜잭션 밖이라 여기서 연다
    @Transactional
    @Modifying
    @Query("update TrustedDevice d set d.tokenHash = :newHash, d.lastUsedAt = :now " +
           "where d.tokenHash = :oldHash and d.memberId = :memberId and d.expiresAt > :now")
    int rotate(@Param("memberId") Long memberId, @Param("oldHash") String oldHash, @Param("newHash") String newHash,
               @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from TrustedDevice d where d.id = :id and d.memberId = :memberId")
    int deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    @Modifying
    @Query("delete from TrustedDevice d where d.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query("delete from TrustedDevice d where d.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
