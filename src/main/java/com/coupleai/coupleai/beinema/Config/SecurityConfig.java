package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
public class SecurityConfig {


    private final JwtAuthenticationFilter jwtAuthenticationFilter;


    private final UserDetailsService userDetailsService;


    private final PasswordEncoder passwordEncoder;


    public SecurityConfig(

            JwtAuthenticationFilter jwtAuthenticationFilter,

            UserDetailsService userDetailsService,

            PasswordEncoder passwordEncoder

    ) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;

        this.userDetailsService = userDetailsService;

        this.passwordEncoder = passwordEncoder;

    }


    @Bean
    public AuthenticationProvider authenticationProvider() {


        DaoAuthenticationProvider provider =

                new DaoAuthenticationProvider(

                        userDetailsService

                );


        provider.setPasswordEncoder(

                passwordEncoder

        );


        return provider;

    }


    @Bean
    public SecurityFilterChain securityFilterChain(

            HttpSecurity http

    ) throws Exception {


        http


                .csrf(

                        AbstractHttpConfigurer::disable

                )


                .cors(

                        cors -> {}

                )


                .authorizeHttpRequests(auth -> auth


                        .requestMatchers(

                                "/api/auth/register",

                                "/api/auth/login",

                                "/api/waitlist",

                                "/api/agents"

                        )

                        .permitAll()


                        .requestMatchers(

                                org.springframework.http.HttpMethod.OPTIONS,

                                "/**"

                        )

                        .permitAll()


                        .anyRequest()

                        .authenticated()

                )


                .authenticationProvider(

                        authenticationProvider()

                )


                .addFilterBefore(

                        jwtAuthenticationFilter,

                        UsernamePasswordAuthenticationFilter.class

                );


        return http.build();

    }

}