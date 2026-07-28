package com.coupleai.coupleai.beinema.DTO.WaitList;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WaitListRequest {

    @NotBlank
    private String contact;
}
