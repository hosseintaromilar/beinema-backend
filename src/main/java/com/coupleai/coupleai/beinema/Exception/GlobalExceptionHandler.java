package com.coupleai.coupleai.beinema.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
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


    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ErrorMessage> handleInactiveUser(

            UserNotActiveException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.FORBIDDEN)

                .body(

                        new ErrorMessage(

                                exception.getMessage()

                        )

                );

    }


    @ExceptionHandler(SmsDeliveryException.class)
    public ResponseEntity<ErrorMessage> handleSmsDelivery(

            SmsDeliveryException exception

    ) {

        return ResponseEntity

                .status(HttpStatus.SERVICE_UNAVAILABLE)

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


    @ExceptionHandler(InsufficientWalletBalanceException.class)
    public ResponseEntity<ErrorMessage> handleInsufficientBalance(
            InsufficientWalletBalanceException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorMessage(exception.getMessage()));
    }


    @ExceptionHandler(AgentAccessExpiredException.class)
    public ResponseEntity<ErrorMessage> handleExpiredAccess(
            AgentAccessExpiredException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorMessage(exception.getMessage()));
    }


    @ExceptionHandler(AgentPurchaseForbiddenException.class)
    public ResponseEntity<ErrorMessage> handlePurchaseForbidden(
            AgentPurchaseForbiddenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorMessage(exception.getMessage()));
    }


    @ExceptionHandler(ParticipationPausedException.class)
    public ResponseEntity<Map<String, Object>> handlePaused(
            ParticipationPausedException exception
    ) {
        Instant until = exception.getPausedUntil();
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "code", "PARTICIPATION_PAUSED",
                        "message", exception.getMessage(),
                        "pausedUntil", until == null ? 0L : until.toEpochMilli(),
                        "remainingSeconds", exception.getRemainingSeconds()
                ));
    }


    @ExceptionHandler(CommitmentRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleCommitment(
            CommitmentRequiredException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage()
                ));
    }


    @ExceptionHandler(SessionEndedException.class)
    public ResponseEntity<Map<String, Object>> handleSessionEnded(
            SessionEndedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "code", "SESSION_ENDED",
                        "message", exception.getMessage()
                ));
    }

}