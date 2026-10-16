package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletRetryService;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.*;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletFacade walletFacade;
    private final WalletRetryService walletRetryService;

    @GetMapping("/me")
    public ResponseEntity<WalletBalanceResponse> getMyWallet(
            @AuthenticationPrincipal SecurityUser user
            ) {
        Long memberId = user.memberId();

        return ResponseEntity.ok(walletFacade.getWallet(memberId));
    }

    @GetMapping("/me/transactions")
    public Page<WalletTransactionResponse> getMyWalletTransactions(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long memberId = user.memberId();

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        Long walletId = wallet.walletId();

        return walletFacade.getTransactions(walletId, pageable);
    }

    @PostMapping("/me/charges")
    public ResponseEntity<WalletChargeOrderResponse> walletChargeOrder(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody WalletChargeOrderRequest chargeOrderRequest
    ) {
        Long memberId = user.memberId();

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        WalletChargeOrder chargeOrder = walletFacade.chargeOrder(
                wallet.walletId(), chargeOrderRequest.amount(), chargeOrderRequest.idempotencyKey());

        return ResponseEntity.ok(WalletChargeOrderResponse.from(chargeOrder));
    }

    @PostMapping("/me/charges/confirm")
    public ResponseEntity<WalletBalanceResponse> walletChargeConfirm(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody WalletChargeConfirmRequest chargeConfirmRequest
            ) {
        Long memberId = user.memberId();

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
