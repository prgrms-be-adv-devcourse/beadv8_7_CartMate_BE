package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.TrustedDevice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 신뢰 기기 쿼리: 확인과 토큰 교체를 UPDATE 하나로, 해제는 행 삭제, 만료 정리
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TrustedDeviceRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @TestConfiguration
    @EnableJpaAuditing
    static class Auditing {
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @Autowired
    private TrustedDeviceRepository repository;

    @Test
    void 이_회원의_만료_안_된_기기이고_해시가_그대로일_때만_교체된다() {
        repository.saveAndFlush(TrustedDevice.register(1L, "old", "Chrome", NOW, NOW.plusDays(30)));

        assertThat(repository.rotate(2L, "old", "new", NOW)).isZero();           // 다른 회원
        assertThat(repository.rotate(1L, "old", "new", NOW.plusDays(31))).isZero(); // 만료
        assertThat(repository.rotate(1L, "old", "new", NOW)).isOne();
        assertThat(repository.rotate(1L, "old", "newer", NOW)).isZero();         // 예전 토큰은 다시 못 씀
    }

    @Test
    void 해제는_내_기기만_지우고_만료된_기기는_정리된다() {
        TrustedDevice mine = repository.saveAndFlush(TrustedDevice.register(1L, "a", "Chrome", NOW, NOW.plusDays(30)));
        repository.saveAndFlush(TrustedDevice.register(1L, "b", "Safari", NOW, NOW.minusDays(1)));
        repository.saveAndFlush(TrustedDevice.register(2L, "c", "Edge", NOW, NOW.plusDays(30)));

        assertThat(repository.deleteByIdAndMemberId(mine.getId(), 2L)).isZero();
        assertThat(repository.findUsable(1L, NOW)).extracting(TrustedDevice::getDeviceName).containsExactly("Chrome");
        assertThat(repository.deleteExpired(NOW)).isOne();
        assertThat(repository.deleteAllByMemberId(1L)).isOne();
        assertThat(repository.count()).isOne();
    }
}
