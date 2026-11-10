package com.coupleai.coupleai.beinema.agents;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** The counselor voice model of each companion, for admins (/api/admin/** in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/voice")
public class VoiceModelController {

    private final VoiceModelService voices;

    public VoiceModelController(VoiceModelService voices) {
        this.voices = voices;
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("voices", voices.list());
        out.put("options", voices.options());
        return out;
    }

    @PostMapping
    public VoiceModelService.Voice set(@RequestBody Map<String, String> body) {
        return voices.set(body.getOrDefault("slug", ""), body.getOrDefault("model", ""));
    }
}
