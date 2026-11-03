package com.coupleai.coupleai.beinema.safety;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A phrase that shows the safety card, editable in the admin panel without a deploy.
 * SELF: the writer may be in danger, the card goes to them. OTHERS: the writer threatens
 * someone, the card goes privately to the other people of the room and never to the writer.
 */
@Entity
@Table(name = "safety_rules")
@Getter
@Setter
@NoArgsConstructor
public class SafetyRule extends BaseEntity {

    public static final String SEXTORTION = "SEXTORTION";
    public static final String VIOLENCE = "VIOLENCE";
    public static final String SELF_HARM = "SELF_HARM";
    public static final String CHILD_ABUSE = "CHILD_ABUSE";

    public static final String SELF = "SELF";
    public static final String OTHERS = "OTHERS";

    @Column(length = 20, nullable = false)
    private String category;

    @Column(length = 120, nullable = false)
    private String pattern;

    @Column(length = 10, nullable = false)
    private String audience = SELF;

    /** fa or finglish; only for the admin's eye. */
    @Column(length = 10, nullable = false)
    private String lang = "fa";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_by")
    private Long updatedBy;
}
