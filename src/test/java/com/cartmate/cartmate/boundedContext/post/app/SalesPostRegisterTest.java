package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesPostRegisterTest {
    @Autowired
    SalesPostCreateUseCase salesPostCreateDraftUseCase;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("판매 게시글이 저장된다")
    void createDraft() {
        CreateSalesPostRequest request =
                new CreateSalesPostRequest(0L, 1L, "아이폰 팝니다", "상태 구웃", List.of());

        SalesPost result = salesPostCreateDraftUseCase.saveSalesPost(request);

        entityManager.flush();
        entityManager.clear();

        SalesPost saved = salesPostRepository.findById(result.getId()).orElseThrow();

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getSellerId()).isEqualTo(1L);
    }

}
