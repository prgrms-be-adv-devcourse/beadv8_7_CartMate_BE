package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletRetryService;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.*;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletWithdrawOrder;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletFacade walletFacade;
    private final WalletRetryService walletRetryService;

    @GetMapping("/me")
    public RsData<WalletBalanceResponse> getMyWallet(
            @AuthenticationPrincipal SecurityUser user
            ) {
        Long memberId = user.memberId();

        return new RsData<>("200-1", "지갑 조회 성공했습니다.", walletFacade.getWallet(memberId));
    }

    @GetMapping("/me/transactions")
    public RsData<Page<WalletTransactionResponse>> getMyWalletTransactions(
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

        return new RsData<>("200-1", "거래 내역 조회 성공했습니다.", walletFacade.getTransactions(walletId, pageable));
    }

    @PostMapping("/me/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<WalletChargeOrderResponse> walletChargeOrder(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody WalletChargeOrderRequest chargeOrderRequest
    ) {
        Long memberId = user.memberId();

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        WalletChargeOrder chargeOrder = walletRetryService.chargeOrder(
                wallet.walletId(),
                chargeOrderRequest.amount(),
                chargeOrderRequest.idempotencyKey()
        );

        return new RsData<>("201-1", "충전 주문이 생성되었습니다.", WalletChargeOrderResponse.from(chargeOrder));
    }

    @PostMapping("/me/charges/confirm")
    public RsData<WalletBalanceResponse> walletChargeConfirm(
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

        return new RsData<>("200-1", "지갑 충전이 완료되었습니다.", walletFacade.getWallet(memberId));
    }

    @PostMapping("/me/withdraws")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<WalletWithdrawOrderResponse> walletWithdrawOrder(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody WalletWithdrawOrderRequest withdrawOrderRequest
    ) {
        Long memberId = user.memberId();

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        WalletWithdrawOrder withdrawOrder = walletRetryService.withdrawOrder(
                wallet.walletId(),
                withdrawOrderRequest.amount(),
                withdrawOrderRequest.idempotencyKey()
        );

        return new RsData<>("201-1", "출금 주문이 생성되었습니다.", WalletWithdrawOrderResponse.from(withdrawOrder));
    }

    @PostMapping("/me/withdraws/confirm")
    public RsData<WalletBalanceResponse> walletWithdrawConfirm(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody WalletWithdrawConfirmRequest withdrawConfirmRequest
    ) {
        Long memberId = user.memberId();

        WalletBalanceResponse wallet = walletFacade.getWallet(memberId);

        walletRetryService.withdraw(
                wallet.walletId(),
                withdrawConfirmRequest.withdrawOrderId()
        );

        return new RsData<>("200-1", "지갑 출금이 완료되었습니다.", walletFacade.getWallet(memberId));
    }
}
