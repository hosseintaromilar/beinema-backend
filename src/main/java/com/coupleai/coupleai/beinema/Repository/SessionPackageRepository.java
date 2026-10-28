package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.SessionPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SessionPackageRepository extends JpaRepository<SessionPackage, Long> {

    /** Bundles still valid with sessions left, the one that ends first first. */
    @Query("""
            select p from SessionPackage p
            where p.userId = :userId and p.expiresAt > :now and p.sessionsUsed < p.sessionsTotal
            order by p.expiresAt asc
            """)
    List<SessionPackage> findUsable(@Param("userId") Long userId, @Param("now") Instant now);

    /** The same, locked: two sessions closing at once do not take the same session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from SessionPackage p
            where p.userId = :userId and p.expiresAt > :now and p.sessionsUsed < p.sessionsTotal
            order by p.expiresAt asc
            """)
    List<SessionPackage> findUsableForUpdate(@Param("userId") Long userId, @Param("now") Instant now);
}
