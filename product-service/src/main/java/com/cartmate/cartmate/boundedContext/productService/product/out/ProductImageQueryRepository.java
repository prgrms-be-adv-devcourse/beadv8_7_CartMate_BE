package com.cartmate.cartmate.boundedContext.productService.product.out;

import java.util.Collection;
import java.util.List;

public interface ProductImageQueryRepository {

    // 고아 사진 파일 정리: 주어진 키 중 DB에 사진 행이 있는 것만(상태와 관계없이, 소프트 삭제된 사진 포함)
    List<String> findExistingS3Keys(Collection<String> s3Keys);
}
