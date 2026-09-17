package com.example.product.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

/** Container-level handler: includes conversion failures before onMessage is called. */
@Component
public class StockListenerErrorHandler implements ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(StockListenerErrorHandler.class);

    @Override
    public void handleError(Throwable failure) {
        log.error("Stock reservation delivery failed; rejecting without requeue to stock-reservation-dlq",
                failure);
        // This is a rejection signal understood by the listener container, not a request to stop it.
        throw new AmqpRejectAndDontRequeueException("Stock reservation delivery rejected", failure);
    }
}
