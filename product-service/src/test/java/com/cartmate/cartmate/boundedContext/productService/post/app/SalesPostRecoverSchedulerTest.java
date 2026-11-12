package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecovery;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.in.SalesPostRecoverScheduler;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.productService.post.domain.QSalesPost.salesPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 판매글 수정 복구 대사 스케줄러: 즉시 복구가 실패했거나(RECOVERING) 수정 중에 서버가 멈춘(PENDING) 판매글을 마무리한다.
 *
 * <p>멈춘 상태는 실제 수정 흐름에서 만든다. 판매글 마무리(markCompleted)를 실패시켜 상품만 수정된 상태로 만들고,
 * 즉시 복구의 상품 복구 API(restoreProducts)를 실패시키면 RECOVERING, 즉시 복구 자체를 건너뛰게 하면 PENDING으로 남는다.
 * "오래 멈춘" 것은 수정 상태가 바뀐 시각(updateStatusChangedAt)을 과거로 돌려 만든다.
 * 테스트는 스케줄링을 끄므로 스케줄러를 직접 만들어 부른다. 상품 API는 실제 HTTP라 웹 서버를 빈 포트로 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SalesPostRecoverSchedulerTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    SalesPostRecoveryUseCase salesPostRecoveryUseCase;
    @MockitoSpyBean
    SalesPostUpdateUseCase salesPostUpdateUseCase;
    @MockitoSpyBean
    SalesPostApiClient salesPostApiClient;
    @MockitoSpyBean
    SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    JPAQueryFactory queryFactory;

    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostRecoveryRepository salesPostRecoveryRepository;
    @Autowired
    SalesPostProductRepository salesPostProductRepository;
    @Autowired
    SalesPostProductImageRepository salesPostProductImageRepository;
    @Autowired
    SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    FakeImageStorage storage;

    SalesPostRecoverScheduler scheduler;

    @BeforeEach
    void setUp() {
        salesPostRecoveryRepository.deleteAll();
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        reset(salesPostUpdateUseCase, salesPostApiClient, salesPostSyncProductUseCase);

        scheduler = new SalesPostRecoverScheduler(salesPostFacade);
    }

    @Test
    @DisplayName("복구 중(RECOVERING)에 오래 멈춘 판매글은 복구를 다시 시도해 상품을 수정 전으로 되돌린다")
    void retryStaleRecovering() {
        Long postId = registerPost("아이폰");
        Long productId = productIdOf(postId);
        leaveRecovering(postId);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("새이름"); // 상품만 수정된 상태
        statusChangedMinutesAgo(postId, 6);

        reset(salesPostApiClient); // 상품 서버가 살아났다
        scheduler.reconcile();

        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERED);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("아이폰");
        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getName()).isEqualTo("아이폰");
        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // 상품은 되돌렸는데 복제본을 못 되돌린 경우. 복제본까지 되돌려야 복구 성공이므로 실패로 세고 다시 시도한다
    @Test
    @DisplayName("복구에서 복제본 되돌리기가 실패하면 복구 실패로 세어 RECOVERING으로 남는다")
    void replicaRestoreFailureCountsAsFailure() {
        Long postId = registerPost("아이폰");
        leaveRecovering(postId);
        statusChangedMinutesAgo(postId, 6);

        reset(salesPostApiClient); // 상품 서버는 살아났다
        doThrow(new RuntimeException("테스트용 복제본 되돌리기 실패"))
                .when(salesPostSyncProductUseCase).syncProduct(anyList());
        scheduler.reconcile();

        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERING);
        assertThat(retryCountOf(postId)).isEqualTo(2); // 즉시 복구 1번 + 이번 1번
        // 수정 마무리(복제본 갱신 포함)가 롤백돼서 복제본은 처음부터 수정 전 값이다
        assertThat(salesPostProductRepository.findById(productIdOf(postId)).orElseThrow().getName()).isEqualTo("아이폰");
    }

    // 즉시 복구 1번 + 스케줄러 2번 = 3번째 실패에서 복구를 포기한다
    @Test
    @DisplayName("복구가 계속 실패하면 3번째 실패에서 복구 실패(RECOVERY_FAILED)로 끝난다")
    void giveUpAfterThreeFailures() {
        Long postId = registerPost("아이폰");
        leaveRecovering(postId);
        statusChangedMinutesAgo(postId, 6);

        scheduler.reconcile();
        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERING);
        assertThat(retryCountOf(postId)).isEqualTo(2);

        scheduler.reconcile();
        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERY_FAILED);
        assertThat(retryCountOf(postId)).isEqualTo(3);
    }

    // 즉시 복구가 아직 진행 중일 수 있으므로 기다린다
    @Test
    @DisplayName("복구 중 · 수정 중이 된 지 얼마 안 된 판매글은 건드리지 않는다")
    void ignoreRecent() {
        Long recoveringId = registerPost("아이폰");
        leaveRecovering(recoveringId);
        Long pendingId = registerPost("갤럭시");
        leavePendingWithSnapshot(pendingId);

        reset(salesPostApiClient);
        scheduler.reconcile();

        assertThat(updateStatusOf(recoveringId)).isEqualTo(SalesPostUpdateStatus.RECOVERING);
        assertThat(retryCountOf(recoveringId)).isEqualTo(1); // 즉시 복구 1번뿐
        assertThat(updateStatusOf(pendingId)).isEqualTo(SalesPostUpdateStatus.PENDING);
    }

    @Test
    @DisplayName("수정 중(PENDING)에 오래 멈췄고 스냅샷이 있으면 상품을 수정 전으로 복구한다")
    void recoverStalePendingWithSnapshot() {
        Long postId = registerPost("아이폰");
        Long productId = productIdOf(postId);
        leavePendingWithSnapshot(postId);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("새이름");
        statusChangedMinutesAgo(postId, 11);

        scheduler.reconcile();

        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERED);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("아이폰");
        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // 스냅샷을 저장하기 전에 멈췄다면 상품은 수정되지 않았으므로 판매글 상태만 수정 전으로 되돌린다
    @Test
    @DisplayName("수정 중(PENDING)에 오래 멈췄고 스냅샷이 없으면 수정 전 상태로 되돌리고 복구 기록을 지운다")
    void revertStalePendingWithoutSnapshot() {
        Long postId = registerPost("아이폰");
        transactionTemplate.executeWithoutResult(s -> {
            SalesPost post = salesPostRepository.findById(postId).orElseThrow();
            SalesPostUpdateStatus previous = salesPostUpdateUseCase.markPending(post);
            salesPostRecoveryUseCase.replace(postId, previous);
        });
        statusChangedMinutesAgo(postId, 11);

        scheduler.reconcile();

        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.NEW);
        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // 상품 서버가 죽어 있으면 계속 시도해도 실패만 하고 재시도 횟수만 소모된다
    @Test
    @DisplayName("복구가 연속 3번 실패하면 이번 실행은 멈추고 나머지 판매글은 다음 실행으로 넘긴다")
    void stopAfterConsecutiveFailures() {
        List<Long> postIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long postId = registerPost("아이폰" + i);
            leaveRecovering(postId);
            statusChangedMinutesAgo(postId, 6);
            postIds.add(postId);
        }

        scheduler.reconcile(); // 상품 복구 API는 계속 실패한다

        assertThat(postIds.subList(0, 3)).allSatisfy(id -> assertThat(retryCountOf(id)).isEqualTo(2));
        assertThat(retryCountOf(postIds.get(3))).isEqualTo(1); // 시도하지 않았다
    }

    // 판매글 + 상품 1개(사진 2장, 옵션 1개)를 실제 등록 흐름으로 만든다
    private Long registerPost(String productName) {
        CreateSalesPostProduct product = new CreateSalesPostProduct(null, null, 1L, productName, "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null),
                        new CreateSalesPostProductImage(null, null, null, 1, null)),
                List.of(new CreateProductOption(1L, 100L)));
        salesPostFacade.registerSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, productName + " 팝니다", "상태 좋아요", List.of(product)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png()));

        return salesPostRepository.findAll().stream()
                .filter(p -> p.getTitle().equals(productName + " 팝니다"))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    // 상품은 수정됐는데 판매글 마무리가 실패하고, 즉시 복구의 상품 복구 API도 실패해 RECOVERING으로 남는다(재시도 1번)
    private void leaveRecovering(Long postId) {
        doThrow(new RuntimeException("테스트용 완료 실패")).when(salesPostUpdateUseCase).markCompleted(anyLong());
        doThrow(new RuntimeException("테스트용 복구 실패")).when(salesPostApiClient).restoreProducts(anyLong(), any());

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(updateRequest(postId), List.of()))
                .hasMessage("테스트용 완료 실패");
        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.RECOVERING);
    }

    // 상품은 수정됐는데 판매글 마무리 직후 서버가 멈춰 즉시 복구가 돌지 못하고 PENDING으로 남는다(스냅샷은 있음)
    private void leavePendingWithSnapshot(Long postId) {
        doThrow(new RuntimeException("테스트용 완료 실패")).when(salesPostUpdateUseCase).markCompleted(anyLong());
        doReturn(false).when(salesPostUpdateUseCase).markRecovering(postId); // 즉시 복구가 PENDING을 가져가지 못함

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(updateRequest(postId), List.of()))
                .hasMessage("테스트용 완료 실패");
        reset(salesPostUpdateUseCase); // 이후(스케줄러)에는 실제로 동작
        assertThat(updateStatusOf(postId)).isEqualTo(SalesPostUpdateStatus.PENDING);
        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId).orElseThrow().getSnapshot()).isNotNull();
    }

    private void statusChangedMinutesAgo(Long postId, int minutes) {
        transactionTemplate.executeWithoutResult(s -> queryFactory.update(salesPost)
                .set(salesPost.updateStatusChangedAt, LocalDateTime.now().minusMinutes(minutes))
                .where(salesPost.id.eq(postId))
                .execute());
    }

    private SalesPostUpdateStatus updateStatusOf(Long postId) {
        return salesPostRepository.findById(postId).orElseThrow().getUpdateStatus();
    }

    private int retryCountOf(Long postId) {
        return salesPostRecoveryRepository.findBySalesPostId(postId).map(SalesPostRecovery::getRetryCount).orElseThrow();
    }

    private Long productIdOf(Long postId) {
        return productRepository.findBySalesPostId(postId).getFirst().getId();
    }

    // 기존 사진 · 옵션은 그대로 두고 상품 이름만 "새이름"으로 바꾸는 요청
    private UpdateSalesPostRequest updateRequest(Long postId) {
        Long productId = productIdOf(postId);
        List<UpdateSalesPostProductImage> images = productImageRepository.findAll().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .sorted(Comparator.comparingInt(ProductImage::getImageSeq))
                .map(i -> new UpdateSalesPostProductImage(i.getId(), productId, i.getS3Key(), i.getImageSeq()))
                .toList();
        List<UpdateSalesPostProductOption> options = productOptionRepository.findAll().stream()
                .filter(o -> o.getProduct().getId().equals(productId))
                .map((ProductOption o) -> new UpdateSalesPostProductOption(o.getId(), o.getProductTypeId(), o.getProductOptionId()))
                .toList();
        UpdateSalesPostProduct product = new UpdateSalesPostProduct(productId, 1L, "새이름", "설명", 30000L,
                new ArrayList<>(images), options);

        return new UpdateSalesPostRequest(postId, SELLER_ID, "새 제목", "새 내용", List.of(product));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
