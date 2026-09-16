package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Agent.AgentPlanResponse;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentResponse;
import com.coupleai.coupleai.beinema.Service.AgentAccessService;
import com.coupleai.coupleai.beinema.Service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {


    private final AgentService agentService;

    private final AgentAccessService agentAccessService;


    @GetMapping
    public ResponseEntity<List<AgentResponse>> getAgents() {


        return ResponseEntity.ok(

                agentService.getActiveAgents()

        );

    }


    @GetMapping("/{id}")
    public ResponseEntity<AgentResponse> getAgent(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                agentService.getAgent(id)
        );
    }


    @GetMapping("/{id}/plans")
    public ResponseEntity<List<AgentPlanResponse>> getPlans(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                agentAccessService.getPlans(id)
        );
    }

    @PostMapping
    public ResponseEntity<AgentResponse> setAgents(
            @RequestBody AgentRequest request
    ) {


        return ResponseEntity.ok(

                agentService.setActiveAgents(request)

        );

    }

}