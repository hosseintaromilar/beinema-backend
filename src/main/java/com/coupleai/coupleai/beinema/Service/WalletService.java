package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Wallet.TopUpRequest;
import com.coupleai.coupleai.beinema.DTO.Wallet.WalletResponse;
import com.coupleai.coupleai.beinema.DTO.Wallet.WalletTransactionResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.Wallet;
import com.coupleai.coupleai.beinema.Entity.WalletTransaction;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Exception.InsufficientWalletBalanceException;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Repository.WalletRepository;
import com.coupleai.coupleai.beinema.Repository.WalletTransactionRepository;
import com.coupleai.coupleai.beinema.Service.payment.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final PaymentProvider paymentProvider;

    @Value("${beinema.wallet.top-up-presets:100000,250000,500000,1000000}")
    private String topUpPresets;

    @Transactional
    public Wallet getOrCreate(User user) {
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> createWallet(user));
    }

    @Transactional
    public Wallet lockByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));
        getOrCreate(user);
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("کیف پول پیدا نشد"));
    }

    @Transactional
    public WalletResponse getWallet(String email) {
        User user = requireUser(email);
        Wallet wallet = getOrCreate(user);
        return WalletResponse.builder()
                .balance(wallet.getBalance())
                .currency("IRT")
                .topUpPresets(parsePresets())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getTransactions(String email) {
        User user = requireUser(email);
        return walletTransactionRepository
                .findTop50ByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WalletResponse topUp(String email, TopUpRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount() < 10000) {
            throw new IllegalArgumentException("مبلغ شارژ باید حداقل ۱۰٬۰۰۰ تومان باشد");
        }
        if (request.getAmount() > 20_000_000) {
            throw new IllegalArgumentException("مبلغ شارژ بیش از حد مجاز است");
        }

        User user = requireUser(email);
        Wallet wallet = lockByUserId(user.getId());
        String reference = paymentProvider.charge(user.getId(), request.getAmount());

        wallet.setBalance(wallet.getBalance() + request.getAmount());
        walletRepository.save(wallet);

        walletTransactionRepository.save(
                WalletTransaction.builder()
                        .user(user)
                        .wallet(wallet)
                        .type(WalletTransactionType.WALLET_TOP_UP)
                        .status(WalletTransactionStatus.SUCCESS)
                        .amount(request.getAmount())
                        .reference(reference)
                        .description("شارژ اعتبار حساب")
                        .build()
        );

        return WalletResponse.builder()
                .balance(wallet.getBalance())
                .currency("IRT")
                .topUpPresets(parsePresets())
                .build();
    }

    @Transactional
    public void assertCanAfford(User user, long amount) {
        if (amount <= 0) {
            return;
        }
        Wallet wallet = getOrCreate(user);
        if (wallet.getBalance() < amount) {
            throw new InsufficientWalletBalanceException(
                    "موجودی کیف پول کافی نیست. لطفاً حساب خود را شارژ کنید."
            );
        }
    }

    @Transactional
    public void debit(
            User user,
            Wallet wallet,
            Long amount,
            WalletTransactionType type,
            String description,
            String reference,
            Long chatRoomId,
            Long agentAccessId
    ) {
        if (wallet.getBalance() < amount) {
            throw new InsufficientWalletBalanceException(
                    "موجودی کیف پول کافی نیست. لطفاً حساب خود را شارژ کنید."
            );
        }

        wallet.setBalance(wallet.getBalance() - amount);
        walletRepository.save(wallet);

        walletTransactionRepository.save(
                WalletTransaction.builder()
                        .user(user)
                        .wallet(wallet)
                        .type(type)
                        .status(WalletTransactionStatus.SUCCESS)
                        .amount(amount)
                        .reference(reference)
                        .description(description)
                        .relatedChatRoomId(chatRoomId)
                        .relatedAgentAccessId(agentAccessId)
                        .build()
        );
    }

    private Wallet createWallet(User user) {
        try {
            return walletRepository.save(
                    Wallet.builder()
                            .user(user)
                            .balance(0L)
                            .build()
            );
        } catch (DataIntegrityViolationException exception) {
            return walletRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> exception);
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));
    }

    private List<Long> parsePresets() {
        return Arrays.stream(topUpPresets.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    private WalletTransactionResponse toResponse(WalletTransaction transaction) {
        return WalletTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .reference(transaction.getReference())
                .createdAt(transaction.getCreatedAt() == null
                        ? null
                        : transaction.getCreatedAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli())
                .build();
    }
}
