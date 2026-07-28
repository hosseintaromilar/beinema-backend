package com.coupleai.coupleai.beinema.Repository;


import com.coupleai.coupleai.beinema.Entity.WaitListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WaitListRepository extends JpaRepository<WaitListEntity, Long> {

        boolean existsByEmail(String email);

        boolean existsByPhoneNumber(String phoneNumber);

}
