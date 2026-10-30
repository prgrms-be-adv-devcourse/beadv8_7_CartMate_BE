package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSellerTrust;
import com.cartmate.cartmate.boundedContext.post.domain.SellerTrustLevel;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerTrustRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({SalesPostSyncSellerTrustUseCase.class, QuerydslConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SalesPostSyncSellerTrustUseCaseTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 3, 0);

    @Autowired
    SalesPostSyncSellerTrustUseCase useCase;
    @Autowired
    SalesPostSellerTrustRepository repository;
    @Autowired
    TestEntityManager em;

    private SellerTrustScoreChangedEvent event(Long sellerId, Integer score, String level, LocalDateTime calculatedAt) {
        return new SellerTrustScoreChangedEvent(sellerId, score, level, calculatedAt);
    }

    @Test
    @DisplayName("신뢰도 변경 이벤트를 받으면 판매자의 신뢰도 복제본이 저장된다")
    void insert() {
        useCase.syncSellerTrust(event(1L, 90, "HIGH", AT));
        em.flush();
        em.clear();

        SalesPostSellerTrust saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getScore()).isEqualTo(90);
        assertThat(saved.getLevel()).isEqualTo(SellerTrustLevel.HIGH);
        assertThat(saved.getCalculatedAt()).isEqualTo(AT);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("판매자 복제 행(SalesPostSeller)이 없어도 신뢰도 복제본은 저장된다")
    void insertWithoutSellerReplica() {
        useCase.syncSellerTrust(event(777L, 70, "NORMAL", AT));
        em.flush();
        em.clear();

        assertThat(repository.findById(777L)).isPresent();
    }

    @Test
    @DisplayName("이미 있는 판매자는 새로 저장하지 않고 점수와 등급을 갱신한다")
    void update() {
        useCase.syncSellerTrust(event(1L, 90, "HIGH", AT));
        em.flush();
        em.clear();

        useCase.syncSellerTrust(event(1L, 60, "LOW", AT.plusDays(1)));
        em.flush();
        em.clear();

        assertThat(repository.count()).isEqualTo(1);
        SalesPostSellerTrust updated = repository.findById(1L).orElseThrow();
        assertThat(updated.getScore()).isEqualTo(60);
        assertThat(updated.getLevel()).isEqualTo(SellerTrustLevel.LOW);
        assertThat(updated.getCalculatedAt()).isEqualTo(AT.plusDays(1));
    }

    @Test
    @DisplayName("저장된 값보다 오래된 이벤트가 늦게 와도 최신 값이 유지된다")
    void ignoreStaleEvent() {
        useCase.syncSellerTrust(event(1L, 60, "LOW", AT.plusDays(1)));
        em.flush();
        em.clear();

        useCase.syncSellerTrust(event(1L, 90, "HIGH", AT));
        em.flush();
        em.clear();

        SalesPostSellerTrust saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getScore()).isEqualTo(60);
        assertThat(saved.getLevel()).isEqualTo(SellerTrustLevel.LOW);
    }

    @Test
    @DisplayName("점수가 없는 NEW 등급 이벤트는 점수 null로 저장된다")
    void insertNewLevel() {
        useCase.syncSellerTrust(event(1L, null, "NEW", AT));
        em.flush();
        em.clear();

        SalesPostSellerTrust saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getScore()).isNull();
        assertThat(saved.getLevel()).isEqualTo(SellerTrustLevel.NEW);
    }

    @Test
    @DisplayName("모르는 등급의 이벤트는 저장하지 않고 예외가 난다")
    void unknownLevel() {
        assertThatThrownBy(() -> useCase.syncSellerTrust(event(1L, 95, "EXCELLENT", AT)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SELLER_TRUST_INVALID");

        em.flush();
        em.clear();
        assertThat(repository.findById(1L)).isEmpty();
    }
}
