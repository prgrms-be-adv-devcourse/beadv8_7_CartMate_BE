package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaveStatus;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
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
    SalesPostCreateDraftUseCase salesPostCreateDraftUseCase;
    @Autowired
    SalesPostWritePostUseCase salesPostWritePostUseCase;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("임시 글이 DRAFT 상태로 DB에 저장된다")
    void createDraft() {
        CreateSalesPostRequest request =
                new CreateSalesPostRequest(0L, 1L, "아이폰 팝니다", "상태 구웃", List.of());

        SalesPost result = salesPostCreateDraftUseCase.createSalesPostDraft(request);

        entityManager.flush();
        entityManager.clear();

        SalesPost saved = salesPostRepository.findById(result.getId()).orElseThrow();

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getSaveStatus()).isEqualTo(SalesPostSaveStatus.DRAFT);
        assertThat(saved.getSellerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("글의 내용이 저장된다")
    void publishSalesPost() {
        CreateSalesPostRequest request =
                new CreateSalesPostRequest(0L, 1L, "아이폰 팝니다", "상태 구웃", List.of());
        salesPostCreateDraftUseCase.createSalesPostDraft(request);
        salesPostWritePostUseCase.publishSalesPost(1L);

        entityManager.flush();
        entityManager.clear();

        SalesPost saved = salesPostRepository.findById(1L).orElseThrow();

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getSaveStatus()).isEqualTo(SalesPostSaveStatus.PUBLISHED);
        assertThat(saved.getSellerId()).isEqualTo(1L);
    }

}
