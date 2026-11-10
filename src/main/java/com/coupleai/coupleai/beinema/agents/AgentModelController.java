package com.coupleai.coupleai.beinema.agents;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The director and voice models of one companion, for admins (/api/admin/** in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/agents/{id}/models")
public class AgentModelController {

    private final AgentModelService models;
    private final AgentRepository agents;

    public AgentModelController(AgentModelService models, AgentRepository agents) {
        this.models = models;
        this.agents = agents;
    }

    @GetMapping
    public Map<String, Object> read(@PathVariable Long id) {
        return models.read(workflow(id));
    }

    /** body: part (DIRECTOR or VOICE), model, thinking (true or false). */
    @PostMapping
    public AgentModelService.State set(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AgentModelService.Part part;
        try {
            part = AgentModelService.Part.valueOf(String.valueOf(body.get("part")));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("بخش نامعتبر است.");
        }
        return models.set(workflow(id), part, String.valueOf(body.get("model")), Boolean.TRUE.equals(body.get("thinking")));
    }

    private String workflow(Long id) {
        Agent agent = agents.findById(id).orElseThrow(() -> new IllegalArgumentException("ایجنت پیدا نشد."));
        if (agent.getAiBotId() == null || agent.getAiBotId().isBlank()) {
            throw new IllegalArgumentException("این ایجنت شناسهٔ ورک‌فلو ندارد.");
        }
        return agent.getAiBotId().trim();
    }
}
