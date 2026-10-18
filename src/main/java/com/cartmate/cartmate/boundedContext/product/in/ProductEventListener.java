package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventListener {
    private final ProductFacade productFacade;

    // 논리 삭제
//    @EventListener
//    public void handle(PostLogicalDeletedEvent event) {
//        productFacade.deleteProductByServer(event.getRequest());
//    }

    @EventListener
    public void handle(PostDeletedEvent event) {
        productFacade.deleteProductByServer(event.getRequest());
    }
}
