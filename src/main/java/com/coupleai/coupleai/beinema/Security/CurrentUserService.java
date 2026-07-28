package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {


    private final UserRepository userRepository;


    public User getCurrentUser() {


        Authentication authentication =

                SecurityContextHolder

                        .getContext()

                        .getAuthentication();


        String email =

                authentication.getName();


        return userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new RuntimeException(

                                "کاربر پیدا نشد"

                        )

                );

    }

}