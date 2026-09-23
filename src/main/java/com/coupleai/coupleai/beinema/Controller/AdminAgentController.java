package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Agent.AdminAgentResponse;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.Service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentService agentService;

    @GetMapping
    public List<AdminAgentResponse> list() {
        return agentService.listForAdmin();
    }

    @PostMapping
    public AdminAgentResponse create(@RequestBody AgentRequest request) {
        return agentService.create(request);
    }

    @PutMapping("/{id}")
    public AdminAgentResponse update(
            @PathVariable Long id,
            @RequestBody AgentRequest request
    ) {
        return agentService.update(id, request);
    }
}
