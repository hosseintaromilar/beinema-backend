package com.coupleai.coupleai.beinema.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(InvalidContactException.class)
    public ResponseEntity<ErrorMessage> handleInvalidContact(

            InvalidContactException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.BAD_REQUEST)

                .body(

                        new ErrorMessage(

                                exception.getMessage()

                        )

                );

    }


    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorMessage> handleEmailAlreadyExists(

            EmailAlreadyExistsException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.CONFLICT)

                .body(

                        new ErrorMessage(

                                exception.getMessage()

                        )

                );

    }


    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorMessage> handleInvalidCredentials(

            InvalidCredentialsException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.UNAUTHORIZED)

                .body(

                        new ErrorMessage(

                                exception.getMessage()

                        )

                );

    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorMessage> handleIllegalArgument(

            IllegalArgumentException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.BAD_REQUEST)

                .body(

                        new ErrorMessage(

                                exception.getMessage()

                        )

                );

    }

}