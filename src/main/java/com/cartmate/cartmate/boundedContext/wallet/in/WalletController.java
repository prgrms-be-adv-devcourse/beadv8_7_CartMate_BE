package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletRetryService;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletFacade walletFacade;
    private final WalletRetryService walletRetryService;

    @GetMapping("/me")
    public ResponseEntity<WalletBalanceResponse> getMyWallet(
            Authentication authentication
    ) {
        Long memberId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(walletFacade.getWallet(memberId));
    }

    @GetMapping("/me/transactions")
    public Page<WalletTransactionResponse> getMyWalletTransactions(
            Authentication authentication,
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long memberId = Long.valueOf(authentication.getName());

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        Long walletId = wallet.walletId();

        return walletFacade.getTransactions(walletId, pageable);
    }

    @PostMapping("/me/charges")
    public ResponseEntity<WalletChargeOrderResponse> walletChargeOrder(
            Authentication authentication,
            @Valid @RequestBody WalletChargeOrderRequest chargeOrderRequest
    ) {
        Long memberId = Long.valueOf(authentication.getName());

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        WalletChargeOrder chargeOrder = walletFacade.chargeOrder(
                wallet.walletId(), chargeOrderRequest.amount(), chargeOrderRequest.idempotencyKey());

        return ResponseEntity.ok(WalletChargeOrderResponse.from(chargeOrder));
    }

    @PostMapping("/me/charges/confirm")
    public ResponseEntity<WalletBalanceResponse> walletChargeConfirm(
            Authentication authentication,
            @Valid @RequestBody WalletChargeConfirmRequest chargeConfirmRequest
            ) {
        Long memberId = Long.valueOf(authentication.getName());

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        walletRetryService.charge(
                wallet.walletId(),
                chargeConfirmRequest.orderNumber(),
                chargeConfirmRequest.paymentKey(),
                chargeConfirmRequest.amount()

        );

        return ResponseEntity.ok(walletFacade.getWallet(memberId));
    }
}
