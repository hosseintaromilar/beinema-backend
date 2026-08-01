package com.coupleai.coupleai.beinema.DTO.messages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @NotBlank(message = "متن پیام الزامی است.")
    @Size(max = 10000, message = "متن پیام بیش از حد طولانی است.")
    private String content;

}