package com.coupleai.coupleai.beinema.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinanceReferenceRepository extends JpaRepository<FinanceReference, Long> {

    Optional<FinanceReference> findTopByOrderByIdDesc();
}
