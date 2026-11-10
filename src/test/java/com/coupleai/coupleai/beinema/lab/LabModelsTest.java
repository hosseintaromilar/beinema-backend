package com.coupleai.coupleai.beinema.lab;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabModelsTest {

    @Test
    void theCostCountsFreshCachedAndOutputTokensAtTheirOwnPrices() {
        LabModels.Model deepseek = LabModels.find("deepseek-v4-1-flash-1017").orElseThrow();
        // 4000 in, 300 out: 4000 * 0.165 + 300 * 0.66 per million
        assertEquals(0.000858, LabModels.cost(deepseek, 4000, 0, 300), 1e-9);
        LabModels.Model sonnet = LabModels.find("claude-sonnet-5-5").orElseThrow();
        // 3500 of the 4000 were cached
        assertEquals((500 * 2.2 + 3500 * 0.22 + 300 * 11.0) / 1_000_000d, LabModels.cost(sonnet, 4000, 3500, 300), 1e-12);
    }

    @Test
    void everyModelOnce() {
        Set<String> seen = new HashSet<>();
        LabModels.ALL.forEach(m -> assertTrue(seen.add(m.model()), m.model()));
        assertTrue(LabModels.find("no-such-model").isEmpty());
    }
}
