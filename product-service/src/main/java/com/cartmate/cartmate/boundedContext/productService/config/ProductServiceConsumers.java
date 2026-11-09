package com.cartmate.cartmate.boundedContext.productService.config;

/**
 * processed_event 테이블에 남기는 처리자 이름. 같은 이벤트를 한 번만 처리하는 데 쓴다({@code ProcessedEvents.markFirst}).
 *
 * <p>이미 쌓인 기록과 맞아야 하므로 한 번 정한 값은 바꾸지 않는다. 바꾸면 처리한 이벤트를 다시 처리한다.
 */
public final class ProductServiceConsumers {

    // product: 판매글 삭제(SalesPostDeletedEvent)를 받아 상품 삭제
    public static final String SALES_POST_DELETED = "product.sales-post-deleted";

    // post: 상품 삭제 완료(ProductsDeletedByPostEvent)를 받아 판매글 삭제 완료
    public static final String PRODUCTS_DELETED_BY_POST = "post.products-deleted-by-post";

    // post: 상품 하나 삭제(ProductDeletedEvent)를 받아 복제본 삭제
    public static final String PRODUCT_DELETED = "post.product-deleted";

    private ProductServiceConsumers() {
    }
}
