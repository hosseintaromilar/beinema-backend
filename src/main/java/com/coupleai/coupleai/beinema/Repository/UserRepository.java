package com.coupleai.coupleai.beinema.Repository;

import com.coupleai.coupleai.beinema.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<User> findByEmailNormalized(String emailNormalized);

    long countByCreatedAtAfterAndSignupIp(java.time.LocalDateTime after, String signupIp);

    java.util.List<User> findAllByEmailVerifiedFalseAndEmailNormalizedIsNotNullAndCreatedAtBefore(java.time.LocalDateTime before);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByGoogleSubject(String googleSubject);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);


    /** For the admin dashboard. */
    long countByCreatedAtAfter(java.time.LocalDateTime since);

    boolean existsBySupervisorTrue();

    java.util.Optional<User> findFirstByRoleOrderByIdAsc(com.coupleai.coupleai.beinema.Enum.UserRole role);

    java.util.List<User> findAllByRole(com.coupleai.coupleai.beinema.Enum.UserRole role);

    @org.springframework.data.jpa.repository.Query("select u from User u where u.deleted = false and (:q = '' "
            + "or lower(u.username) like concat('%', :q, '%') or lower(u.name) like concat('%', :q, '%') "
            + "or lower(u.email) like concat('%', :q, '%') or u.phoneNumber like concat('%', :q, '%')) order by u.id desc")
    java.util.List<User> searchForAdmin(@org.springframework.data.repository.query.Param("q") String q, org.springframework.data.domain.Pageable page);
}
