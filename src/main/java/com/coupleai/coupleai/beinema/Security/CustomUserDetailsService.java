package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService
        implements UserDetailsService {


    private final UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(

            String email

    ) throws UsernameNotFoundException {


        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new UsernameNotFoundException(

                                "کاربر پیدا نشد"

                        )

                );


        return org.springframework.security.core.userdetails.User

                .withUsername(

                        user.getEmail()

                )

                .password(

                        user.getPassword()

                )

                .authorities(

                        new SimpleGrantedAuthority(

                                "ROLE_" +

                                        user.getRole().name()

                        )

                )

                .accountExpired(

                        false

                )

                .accountLocked(

                        false

                )

                .credentialsExpired(

                        false

                )

                .disabled(

                        user.getStatus() !=

                                UserStatus.ACTIVE

                )

                .build();

    }

}