package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.User.UserResponse;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;


    public UserResponse getCurrentUser(

            Authentication authentication

    ) {

        String email = authentication.getName();


        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new RuntimeException(

                                "کاربر پیدا نشد"

                        )

                );


        return UserResponse.from(user);

    }

}