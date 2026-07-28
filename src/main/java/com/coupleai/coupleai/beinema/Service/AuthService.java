package com.coupleai.coupleai.beinema.Service;


import com.coupleai.coupleai.beinema.DTO.Auth.AuthResponse;
import com.coupleai.coupleai.beinema.DTO.Auth.LoginRequest;
import com.coupleai.coupleai.beinema.DTO.Auth.RegisterRequest;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Exception.EmailAlreadyExistsException;
import com.coupleai.coupleai.beinema.Exception.InvalidCredentialsException;
import com.coupleai.coupleai.beinema.Exception.UserNotActiveException;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;


@Service

@RequiredArgsConstructor

@Transactional

public class AuthService {


    private final UserRepository userRepository;


    private final PasswordEncoder passwordEncoder;


    private final JwtService jwtService;



    public AuthResponse register(

            RegisterRequest request

    ) {


        String email = normalizeEmail(

                request.getEmail()

        );



        if (

                userRepository.existsByEmail(email)

        ) {

            throw new EmailAlreadyExistsException(

                    "این ایمیل قبلاً ثبت‌نام کرده است"

            );

        }



        User user = User.builder()

                .name(request.getName())

                .email(request.getEmail())

                .password(

                        passwordEncoder.encode(

                                request.getPassword()

                        )

                )

                .status(UserStatus.ACTIVE)

                .role(UserRole.USER)

                .emailVerified(false)

                .build();



        User savedUser = userRepository.save(

                user

        );



        String token = jwtService.generateToken(

                savedUser.getId(),

                savedUser.getEmail()

        );



        return new AuthResponse(

                "ثبت‌نام با موفقیت انجام شد",

                token

        );

    }



    public AuthResponse login(

            LoginRequest request

    ) {


        String email = normalizeEmail(

                request.getEmail()

        );



        User user = userRepository

                .findByEmail(email)

                .orElseThrow(() ->

                        new InvalidCredentialsException(

                                "کاربری بااین ایمیل پیدا نشد"

                        )

                );



        if (

                !user.isActive()

        ) {

            throw new UserNotActiveException(

                    "حساب کاربری شما فعال نیست"

            );

        }



        if (

                !passwordEncoder.matches(

                        request.getPassword(),

                        user.getPassword()

                )

        ) {

            throw new InvalidCredentialsException(

                    "ایمیل یا رمز عبور اشتباه است"

            );

        }



        user.updateLastLogin();



        String token = jwtService.generateToken(

                user.getId(),

                user.getEmail()

        );



        return new AuthResponse(

                "ورود با موفقیت انجام شد",

                token

        );

    }



    private String normalizeEmail(

            String email

    ) {


        return email

                .trim()

                .toLowerCase();

    }

}