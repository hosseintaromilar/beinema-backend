package com.coupleai.coupleai.beinema.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {


    private final JwtService jwtService;


    private final UserDetailsService userDetailsService;


    private final TokenBlacklistService tokenBlacklistService;


    private final RequestAttributeSecurityContextRepository
            securityContextRepository =
            new RequestAttributeSecurityContextRepository();


    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }


    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();
        String method = request.getMethod();

        if (path == null) {
            return false;
        }

        if ("POST".equalsIgnoreCase(method)
                && ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path))) {
            return true;
        }

        return "GET".equalsIgnoreCase(method)
                && path.startsWith("/api/invitations/")
                && !path.endsWith("/accept");
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader =
                request.getHeader("Authorization");



        if (
                authHeader == null ||
                        !authHeader.startsWith("Bearer ")
        ) {


            filterChain.doFilter(request, response);

            return;
        }

        String token = authHeader.substring(7);

        if (tokenBlacklistService.isRevoked(token)) {

            filterChain.doFilter(request, response);

            return;
        }

        String email;

        try {

            email = jwtService.extractUsername(token);



        } catch (Exception exception) {



            filterChain.doFilter(request, response);

            return;
        }

        if (
                email != null &&
                        SecurityContextHolder
                                .getContext()
                                .getAuthentication() == null
        ) {

            UserDetails userDetails;

            try {

                userDetails =
                        userDetailsService
                                .loadUserByUsername(email);

            } catch (Exception exception) {

                filterChain.doFilter(request, response);

                return;
            }

            boolean valid =
                    jwtService.isTokenValid(token);



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

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);

                SecurityContextHolder.setContext(context);

                securityContextRepository.saveContext(
                        context,
                        request,
                        response
                );


            }
        }

        filterChain.doFilter(request, response);
    }
}