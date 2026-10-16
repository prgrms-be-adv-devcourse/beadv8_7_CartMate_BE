package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSeller;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerRepository;
import com.cartmate.cartmate.shared.member.dto.SellerDto;
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


@DataJpaTest
@Import(SalesPostSyncSellerUseCase.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class SalesPostSyncSellerUseCaseTest {

    @Autowired SalesPostSyncSellerUseCase salesPostSyncSellerUseCase;
    @Autowired
    SalesPostSellerRepository salesPostSellerRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("판매자 생성 이벤트 수신 시 저장된다.")
    void sellerInsert() {
        salesPostSyncSellerUseCase.syncSeller(sellerDto("첫번째 상점"));
        em.flush();
        em.clear();

        SalesPostSeller saved = salesPostSellerRepository.findById(1L).orElseThrow();
        assertThat(saved.getBusinessName()).isEqualTo("첫번째 상점");
    }

    @Test
    @DisplayName("이미 있는 판매자는 새로 저장되지 않고 정보가 수정된다.")
    void sellerUpdate() {
        salesPostSyncSellerUseCase.syncSeller(sellerDto("첫번째 상점"));
        em.flush();
        em.clear();

        salesPostSyncSellerUseCase.syncSeller(sellerDto("두번째 상점"));
        em.flush();
        em.clear();

        assertThat(salesPostSellerRepository.count()).isEqualTo(1);
        SalesPostSeller updated = salesPostSellerRepository.findById(1L).orElseThrow();
        assertThat(updated.getBusinessName()).isEqualTo("두번째 상점");
    }

    private SellerDto sellerDto(String businessName) {
        return new SellerDto(
                1L,
                1L,
                "INDIVIDUAL",
                businessName,
                "123-45-67890",
                "010-1234-5678",
                "국민은행",
                "홍길동",
                "1002-123-456789",
                "ACTIVE",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

}
