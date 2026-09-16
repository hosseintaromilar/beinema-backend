package com.coupleai.coupleai.beinema.DTO.Agent;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import lombok.Builder;

import java.util.Arrays;
import java.util.List;

@Builder
public record AgentResponse(

        Long id,

        String name,

        AgentType type,

        String description,

        String avatar,

        String photoUrl,

        String headline,

        String bio,

        List<String> specialties,

        Long accessFee,

        Integer accessDurationDays

) {

    public static AgentResponse from(Agent agent) {

        return from(agent, null);

    }

    public static AgentResponse from(Agent agent, AgentPlan plan) {

        String photo = firstNonBlank(agent.getPhotoUrl(), agent.getAvatar());

        return AgentResponse.builder()

                .id(agent.getId())
                .name(agent.getName())
                .type(agent.getType())
                .description(agent.getDescription())
                .avatar(photo)
                .photoUrl(photo)
                .headline(agent.getHeadline())
                .bio(agent.getBio())
                .specialties(splitSpecialties(agent.getSpecialties()))
                .accessFee(plan == null ? null : plan.getPrice())
                .accessDurationDays(plan == null ? null : plan.getDurationDays())
                .build();

    }

    private static List<String> splitSpecialties(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,،]"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

}
