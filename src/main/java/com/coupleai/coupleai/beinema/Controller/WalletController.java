package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Wallet.TopUpRequest;
import com.coupleai.coupleai.beinema.DTO.Wallet.WalletResponse;
import com.coupleai.coupleai.beinema.DTO.Wallet.WalletTransactionResponse;
import com.coupleai.coupleai.beinema.Service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(Authentication authentication) {
        return ResponseEntity.ok(
                walletService.getWallet(authentication.getName())
        );
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactions(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                walletService.getTransactions(authentication.getName())
        );
    }

    @PostMapping("/top-up")
    public ResponseEntity<WalletResponse> topUp(
            Authentication authentication,
            @RequestBody TopUpRequest request
    ) {
        return ResponseEntity.ok(
                walletService.topUp(authentication.getName(), request)
        );
    }
}
