package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletFacade walletFacade;

    @GetMapping("/me")
    public ResponseEntity<WalletBalanceResponse> getMyWallet(
            Authentication authentication
    ) {
        Long memberId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(walletFacade.getWallet(memberId));
    }
}
