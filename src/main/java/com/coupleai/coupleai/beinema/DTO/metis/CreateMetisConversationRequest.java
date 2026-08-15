package com.coupleai.coupleai.beinema.DTO.metis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMetisConversationRequest {

    private String botId;

    private MetisUserRequest user;
}