package com.coupleai.coupleai.beinema.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    List<EmailCampaign> findTop100ByOrderByIdDesc();

    boolean existsByKindAndStatus(String kind, String status);
}
