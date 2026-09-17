package com.example.product.messaging;

import com.example.product.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StockListenerErrorHandlerTest {
    @Test
    void malformedJsonConversionFailureIsRejectedWithoutRequeue() {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(StockReservationMessage.class);
        var message = new Message("{not-json".getBytes(StandardCharsets.UTF_8), properties);
        var converter = new RabbitConfig().jsonMessageConverter();
        var conversion = assertThrows(MessageConversionException.class,
                () -> converter.fromMessage(message));
        var deliveryFailure = new ListenerExecutionFailedException("Cannot convert", conversion, message);
        var rejection = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> new StockListenerErrorHandler().handleError(deliveryFailure));
        assertSame(deliveryFailure, rejection.getCause());
    }

    @Test
    void topologyRoutesRejectedMessagesToDurableDlq() {
        var config = new RabbitConfig();
        var main = config.stockReservationQueue();
        var dead = config.stockDeadLetterQueue();
        var exchange = config.stockDeadLetterExchange();
        var binding = config.stockDeadLetterBinding(dead, exchange);
        assertTrue(main.isDurable());
        assertTrue(dead.isDurable());
        assertTrue(exchange.isDurable());
        assertEquals("direct", exchange.getType());
        assertEquals("stock-dlx", main.getArguments().get("x-dead-letter-exchange"));
        assertEquals("stock-reservation-dlq", main.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("stock-reservation-dlq", binding.getDestination());
        assertEquals("stock-dlx", binding.getExchange());
        assertEquals("stock-reservation-dlq", binding.getRoutingKey());
    }
}
