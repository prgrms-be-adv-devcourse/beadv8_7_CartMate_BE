package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletChargeWebhookService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 토스페이먼츠 웹훅 수신. 결제 상태가 바뀌면 토스가 호출하며, 200이 아니면 다시 보낸다.
// 본문은 신뢰하지 않고 paymentKey만 꺼내 서비스에서 토스에 다시 조회한다.
@Slf4j
@RestController
@RequestMapping("/api/wallets/webhooks/toss")
@RequiredArgsConstructor
public class TossWebhookController {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossWebhookRequest(String eventType, Data data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Data(String paymentKey) {
        }
    }

    private final WalletChargeWebhookService webhookService;

    @PostMapping
    public void receive(@RequestBody TossWebhookRequest request) {
        if (!PAYMENT_STATUS_CHANGED.equals(request.eventType())
                || request.data() == null
                || request.data().paymentKey() == null) {
            log.info("처리하지 않는 토스 웹훅: eventType={}", request.eventType());
            return;
        }

        webhookService.sync(request.data().paymentKey());
    }
}
