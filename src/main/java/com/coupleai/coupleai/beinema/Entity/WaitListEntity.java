package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitListEntity extends BaseEntity {

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

}
