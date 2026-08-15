package com.coupleai.coupleai.beinema.DTO.metis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetisConversationResponse {

    private String id;

    private String botId;

    private MetisUserResponse user;

    private Object messages;

    private Long startDate;

    private Long lastUpdateDate;

    private String headline;
}