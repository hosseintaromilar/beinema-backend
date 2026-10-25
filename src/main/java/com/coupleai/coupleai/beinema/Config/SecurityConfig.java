package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Security.CustomUserDetailsService;
import com.coupleai.coupleai.beinema.Security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.util.List;


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


                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )


                .securityContext(context ->
                        context.securityContextRepository(
                                new RequestAttributeSecurityContextRepository()
                        )
                )


                .authorizeHttpRequests(auth -> auth


                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        )
                        .permitAll()


                        .requestMatchers(
                                "/error",
                                "/error/**"
                        )
                        .permitAll()


                        .requestMatchers(

                                "/api/auth/register",

                                "/api/auth/login",

                                "/api/auth/forgot-password",

                                "/api/auth/reset-password",

                                "/api/auth/otp/request",

                                "/api/auth/otp/verify",

                                "/api/auth/google",

                                "/api/auth/google/config",

                                "/api/waitlist"

                        )

                        .permitAll()


                        .requestMatchers(

                                org.springframework.http.HttpMethod.GET,

                                "/api/agents",

                                "/api/agents/*",

                                "/api/agents/*/plans",

                                "/uploads/**"

                        )

                        .permitAll()


                        .requestMatchers(

                                org.springframework.http.HttpMethod.GET,

                                "/api/invitations/**"

                        )

                        .permitAll()


                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/referrals/preview/**"
                        )
                        .permitAll()


                        .requestMatchers(

                                org.springframework.http.HttpMethod.OPTIONS,

                                "/**"

                        )

                        .permitAll()


                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")


                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/api/agents"
                        )
                        .hasRole("ADMIN")


                        // a Google user without a verified phone may only finish signing in
                        .requestMatchers(
                                "/api/auth/phone/**",
                                "/api/auth/logout",
                                "/api/users/me",
                                "/api/users/me/name"
                        )
                        .authenticated()


                        .anyRequest()

                        .access((authentication, context) ->
                                new AuthorizationDecision(phoneConfirmed(authentication.get())))

                )


                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"message\":\"برای ادامه باید وارد حساب شوید.\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    hasAuthority(
                                            org.springframework.security.core.context.SecurityContextHolder
                                                    .getContext().getAuthentication(),
                                            CustomUserDetailsService.PHONE_PENDING
                                    )
                                            ? "{\"code\":\"PHONE_REQUIRED\",\"message\":\"اول شماره موبایل خود را تأیید کنید.\"}"
                                            : "{\"message\":\"این بخش فقط برای ادمین است.\"}"
                            );
                        })
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


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(
                List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://192.168.*:*",
                        "http://10.*:*",
                        "http://172.16.*:*",
                        "http://172.17.*:*",
                        "http://172.18.*:*",
                        "http://172.19.*:*",
                        "http://172.20.*:*",
                        "http://172.21.*:*",
                        "http://172.22.*:*",
                        "http://172.23.*:*",
                        "http://172.24.*:*",
                        "http://172.25.*:*",
                        "http://172.26.*:*",
                        "http://172.27.*:*",
                        "http://172.28.*:*",
                        "http://172.29.*:*",
                        "http://172.30.*:*",
                        "http://172.31.*:*"
                )
        );
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    private static boolean phoneConfirmed(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !hasAuthority(authentication, CustomUserDetailsService.PHONE_PENDING);
    }


    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

}
