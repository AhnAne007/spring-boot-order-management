package com.example.order.messaging;

import com.example.order.exception.MessagingUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import static com.example.order.config.RabbitConfig.QUEUE;

@Service
public class OrderMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderMessagePublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public OrderMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @CircuitBreaker(name = "stockPublisher", fallbackMethod = "publishFallback")
    public void publish(StockReservationMessage msg) {
        rabbitTemplate.convertAndSend(QUEUE, msg);
    }

    private void publishFallback(StockReservationMessage msg, Throwable t) {
        log.warn("Stock publishing unavailable for orderId={}: {}: {}",
                msg.orderId(), t.getClass().getSimpleName(), t.getMessage());
        throw new MessagingUnavailableException(msg.orderId(), t);
    }
}
