package com.coupleai.coupleai.beinema.agents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every companion in one place: classpath:agents/<slug>.json says its Metis workflow, the
 * workflows it used before, its kind, what it can do beyond talking, and the texts it starts
 * with. A new companion is a new file and a Metis workflow; the engine stays as it is.
 */
public final class AgentPacks {

    /** What a companion can do beyond the conversation itself. */
    public enum Ability {
        /** works by its own method, not by Reality Therapy: no choice-theory rules or lessons */
        OWN_METHOD,
        /** the tests and visual lessons of kb/elika-kit.json */
        KIT,
        /** today's market prices in its packet */
        MARKET,
        /** teaches with private lesson cards (TeachKit) */
        TEACH
    }

    /** The texts a companion starts with; after that the admin panel owns them. */
    public record Seed(String name, String headline, String description, String bio, String specialties, String photo) {
    }

    public record Pack(String slug, String workflowId, List<String> formerWorkflowIds, String type,
                       Set<Ability> abilities, Seed seed) {

        public boolean can(Ability ability) {
            return abilities != null && abilities.contains(ability);
        }

        public boolean runsOn(String botId) {
            return botId != null && workflowId.equals(botId.trim());
        }
    }

    static final String PATTERN = "classpath*:agents/*.json";

    private static final List<Pack> ALL = load();

    private AgentPacks() {
    }

    public static List<Pack> all() {
        return ALL;
    }

    public static Optional<Pack> byWorkflow(String botId) {
        return ALL.stream().filter(pack -> pack.runsOn(botId)).findFirst();
    }

    private static List<Pack> load() {
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Pack> packs = new ArrayList<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
                try (InputStream in = resource.getInputStream()) {
                    packs.add(check(json.readValue(in, Pack.class), resource.getFilename()));
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read the companions in agents/", failure);
        }
        packs.sort(Comparator.comparing(Pack::slug));
        return List.copyOf(packs);
    }

    private static Pack check(Pack pack, String file) {
        if (pack.slug() == null || pack.slug().isBlank() || pack.workflowId() == null || pack.workflowId().isBlank()
                || pack.type() == null || pack.seed() == null || pack.seed().name() == null) {
            throw new IllegalStateException("agents/" + file + " needs a slug, a workflowId, a type and a seed name");
        }
        return new Pack(pack.slug(), pack.workflowId().trim(),
                pack.formerWorkflowIds() == null ? List.of() : List.copyOf(pack.formerWorkflowIds()),
                pack.type(), pack.abilities() == null ? Set.of() : Set.copyOf(pack.abilities()), pack.seed());
    }
}
