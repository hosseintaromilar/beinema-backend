package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Entity.PromoTierCounter;
import com.coupleai.coupleai.beinema.Enum.SessionPackageTier;
import com.coupleai.coupleai.beinema.Repository.PromoTierCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** One counter row for every discount step of every plan, starting at zero; never reset. */
@Component
@RequiredArgsConstructor
public class PricingSeeder implements ApplicationRunner {

    private final PromoTierCounterRepository counterRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (SessionPackageTier plan : SessionPackageTier.values()) {
            for (SessionPackageTier.Step step : plan.steps()) {
                if (counterRepository.findByPlanAndTierNo(plan.name(), step.number()).isEmpty()) {
                    PromoTierCounter counter = new PromoTierCounter();
                    counter.setPlan(plan.name());
                    counter.setTierNo(step.number());
                    counter.setCapacity(step.capacity());
                    counter.setSold(0);
                    counterRepository.save(counter);
                }
            }
        }
    }
}
