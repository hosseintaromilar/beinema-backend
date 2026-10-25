package com.coupleai.coupleai.beinema.Security;

import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

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

                        authoritiesOf(user)

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

                        // blocked or deleted (e.g. the empty account retired when a Google login joins a phone account)
                        !user.isActive()

                )

                .build();

    }



    /** Marks a Google user whose phone number is not verified yet (see SecurityConfig). */
    public static final String PHONE_PENDING = "PHONE_PENDING";


    private static List<SimpleGrantedAuthority> authoritiesOf(User user) {
        SimpleGrantedAuthority role = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            return List.of(role, new SimpleGrantedAuthority(PHONE_PENDING));
        }
        return List.of(role);
    }

}
