package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.FreeSessionGrant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeSessionGrantRepository extends JpaRepository<FreeSessionGrant, Long> {

    boolean existsByEmailNormalized(String emailNormalized);
}
