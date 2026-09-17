package com.example.order.exception;

public class MessagingUnavailableException extends RuntimeException {
    private final String orderId;

    public MessagingUnavailableException(String orderId, Throwable cause) {
        super("Messaging is temporarily unavailable for order " + orderId
                + ". Stock reservation could not be submitted.", cause);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
