package com.coupleai.coupleai.beinema.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {


    private final JwtService jwtService;


    private final UserDetailsService userDetailsService;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader =
                request.getHeader("Authorization");

        System.out.println(
                "REQUEST: " + request.getMethod()
                        + " "
                        + request.getRequestURI()
        );

        System.out.println(
                "AUTH HEADER: " + authHeader
        );

        if (
                authHeader == null ||
                        !authHeader.startsWith("Bearer ")
        ) {

            System.out.println(
                    "NO VALID BEARER TOKEN"
            );

            filterChain.doFilter(request, response);

            return;
        }

        String token = authHeader.substring(7);

        String email;

        try {

            email = jwtService.extractUsername(token);

            System.out.println(
                    "TOKEN EMAIL: " + email
            );

        } catch (Exception exception) {

            System.out.println(
                    "TOKEN PARSING FAILED: "
                            + exception.getMessage()
            );

            filterChain.doFilter(request, response);

            return;
        }

        if (
                email != null &&
                        SecurityContextHolder
                                .getContext()
                                .getAuthentication() == null
        ) {

            UserDetails userDetails =
                    userDetailsService
                            .loadUserByUsername(email);

            boolean valid =
                    jwtService.isTokenValid(token);

            System.out.println(
                    "TOKEN VALID: " + valid
            );

            if (valid) {

                UsernamePasswordAuthenticationToken
                        authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(
                                authentication
                        );

                System.out.println(
                        "AUTHENTICATION SET SUCCESSFULLY"
                );
            }
        }

        filterChain.doFilter(request, response);
    }
}