package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.DTO.Agent.AdminAgentResponse;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.Service.AgentService;
import com.coupleai.coupleai.beinema.Service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentService agentService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public List<AdminAgentResponse> list() {
        return agentService.listForAdmin();
    }

    @PostMapping
    public AdminAgentResponse create(@RequestBody AgentRequest request) {
        return agentService.create(request);
    }

    /** Stores an agent photo and returns its address; the form saves it with the agent. */
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadPhoto(@RequestParam("file") MultipartFile file) {
        return Map.of("photoUrl", fileStorageService.storeAgentPhoto(file));
    }

    @PutMapping("/{id}")
    public AdminAgentResponse update(
            @PathVariable Long id,
            @RequestBody AgentRequest request
    ) {
        return agentService.update(id, request);
    }
}
