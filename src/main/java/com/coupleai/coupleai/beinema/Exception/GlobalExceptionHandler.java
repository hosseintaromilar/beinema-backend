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


    /** The same answer for a room that does not exist and one you are not in, so nothing can be learned from it. */
    @ExceptionHandler(ChatRoomAccessDeniedException.class)
    public ResponseEntity<java.util.Map<String, String>> roomDenied(ChatRoomAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("message", "این گفتگو پیدا نشد."));
    }

    /** The AI service is down: one plain sentence, and a code the site knows. */
    @ExceptionHandler(com.coupleai.coupleai.beinema.ai.AiUnavailableException.class)
    public ResponseEntity<java.util.Map<String, String>> aiUnavailable(com.coupleai.coupleai.beinema.ai.AiUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("code", "AI_UNAVAILABLE", "message", exception.getMessage()));
    }

    /** The price of a plan changed before the purchase: nothing was bought; the new price goes back. */
    @ExceptionHandler(com.coupleai.coupleai.beinema.Service.SessionBillingService.PriceChangedException.class)
    public ResponseEntity<java.util.Map<String, Object>> priceChanged(com.coupleai.coupleai.beinema.Service.SessionBillingService.PriceChangedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("code", "PRICE_CHANGED", "message", exception.getMessage(), "price", exception.price()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<java.util.Map<String, String>> plainFailure(RuntimeException exception) {
        String text = exception.getMessage();
        if ("Access denied".equals(text) || "Chat room not found".equals(text)) {
            // also for a live stream, whose request accepts only events
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("message", "این گفتگو پیدا نشد."));
        }
        throw exception;
    }

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

                                forThePerson(exception.getMessage())

                        )

                );

    }


    /**
     * Our own messages are written for the person in Persian. A message from
     * inside Java or a library ("For input string: ...") is not for them.
     */
    static String forThePerson(String message) {
        if (message != null && message.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF)) {
            return message;
        }
        return "درخواست انجام نشد. صفحه را تازه کن و دوباره امتحان کن.";
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


    @ExceptionHandler(QuestionAnswerException.class)
    public ResponseEntity<Map<String, Object>> handleQuestionAnswer(
            QuestionAnswerException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage()
                ));
    }


    @ExceptionHandler(SessionOnBreakException.class)
    public ResponseEntity<Map<String, Object>> handleSessionOnBreak(
            SessionOnBreakException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
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