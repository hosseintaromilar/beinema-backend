package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.PromoTierCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoTierCounterRepository extends JpaRepository<PromoTierCounter, Long> {

    Optional<PromoTierCounter> findByPlanAndTierNo(String plan, Integer tierNo);

    /** Takes one place of a step if one is left; 1 when it was taken, 0 when the step is full. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PromoTierCounter c set c.sold = c.sold + 1 where c.plan = :plan and c.tierNo = :tier and c.sold < c.capacity")
    int take(@Param("plan") String plan, @Param("tier") Integer tier);
}
