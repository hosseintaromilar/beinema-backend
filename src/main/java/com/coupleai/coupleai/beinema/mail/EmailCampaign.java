package com.coupleai.coupleai.beinema.mail;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An email the admin sent from the panel: to one person, or to a group or everyone. The counts
 * fill in while it goes out (a group is sent slowly, so the new domain keeps a good name).
 */
@Entity
@Table(name = "email_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class EmailCampaign extends BaseEntity {

    public static final String SENDING = "SENDING";
    public static final String DONE = "DONE";

    private Long senderId;

    /** no-reply, support or info. */
    @Column(length = 20, nullable = false)
    private String fromKey;

    @Column(length = 150, nullable = false)
    private String subject;

    @Column(length = 8000, nullable = false)
    private String body;

    /** single: one person, group: a group or everyone. */
    @Column(length = 10, nullable = false)
    private String kind;

    @Column(length = 300)
    private String audienceFilter;

    @Column(length = 10, nullable = false)
    private String status;

    private int recipients;
    private int sent;
    private int failed;

    /** People in the group with no email address, or who asked for no group emails. */
    private int skipped;

    private Instant finishedAt;
}
