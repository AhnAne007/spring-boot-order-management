package com.example.order.exception;

import com.example.order.dto.response.MessagingErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MessagingUnavailableException.class)
    public ResponseEntity<MessagingErrorResponse> messagingUnavailable(MessagingUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new MessagingErrorResponse(ex.getMessage(), ex.getOrderId()));
    }
}
