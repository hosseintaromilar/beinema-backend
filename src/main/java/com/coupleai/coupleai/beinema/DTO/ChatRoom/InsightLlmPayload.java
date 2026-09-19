package com.coupleai.coupleai.beinema.DTO.ChatRoom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsightLlmPayload {

    private String whereWeWere;

    private String whereWeAre;

    private String status;

    private List<String> needs;

    private List<String> patterns;

    private String nextStep;

    private String dailySentence;
}
