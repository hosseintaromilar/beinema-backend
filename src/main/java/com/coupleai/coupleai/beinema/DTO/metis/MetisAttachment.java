package com.coupleai.coupleai.beinema.DTO.metis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An attachment of a Metis message: a public direct-download URL plus its kind. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetisAttachment {

    private String content;

    /** TEXT, IMAGE, FILE, AUDIO or VIDEO. */
    private String contentType;
}
