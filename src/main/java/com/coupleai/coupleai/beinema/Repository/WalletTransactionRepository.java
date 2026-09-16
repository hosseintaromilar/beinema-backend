package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);
}
