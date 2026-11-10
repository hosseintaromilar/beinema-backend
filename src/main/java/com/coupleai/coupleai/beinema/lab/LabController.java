package com.coupleai.coupleai.beinema.lab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The admin's model lab (/api/admin/** is for admins only, in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/lab")
public class LabController {

    private final LabService lab;

    public LabController(LabService lab) {
        this.lab = lab;
    }

    @GetMapping
    public Map<String, Object> start() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("models", lab.models());
        out.put("samples", lab.samples());
        out.put("dollar", lab.dollar());
        return out;
    }

    @GetMapping("/voice-prompt")
    public Map<String, Object> voicePrompt(@RequestParam String agent) {
        return Map.of("prompt", lab.voicePrompt(agent));
    }

    @PostMapping("/run")
    public LabService.Result run(@RequestBody LabService.Run run) {
        return lab.run(run);
    }

    /** The same input to several models at once, in parallel. */
    @PostMapping("/compare")
    public List<LabService.Result> compare(@RequestBody Map<String, Object> body) {
        Object models = body.get("models");
        if (!(models instanceof List<?> list) || list.isEmpty() || list.size() > 6) {
            throw new IllegalArgumentException("یک تا شش مدل انتخاب کن.");
        }
        String system = body.get("system") == null ? "" : body.get("system").toString();
        String user = body.get("user") == null ? "" : body.get("user").toString();
        Double temperature = body.get("temperature") instanceof Number n ? n.doubleValue() : null;
        Integer maxTokens = body.get("maxTokens") instanceof Number n ? n.intValue() : null;
        return list.parallelStream()
                .map(model -> lab.run(new LabService.Run(model.toString(), system, user, temperature, maxTokens)))
                .toList();
    }
}
