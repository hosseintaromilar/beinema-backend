package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** With no supervisor yet, the first admin becomes the supervisor. */
@Component
@RequiredArgsConstructor
public class SupervisorSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsBySupervisorTrue()) {
            return;
        }
        userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN).ifPresent(admin -> {
            admin.setSupervisor(true);
            userRepository.save(admin);
        });
    }
}
