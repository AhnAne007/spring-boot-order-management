package com.example.order.messaging;

import com.example.order.exception.MessagingUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.example.order.config.RabbitConfig.QUEUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderMessagePublisherTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderMessagePublisher publisher;

    @Test
    void happyPathPublishesExactlyOnceWithQueueAndPayload() {
        StockReservationMessage message = new StockReservationMessage(1L, 3, "order-1");

        publisher.publish(message);

        verify(rabbitTemplate, times(1)).convertAndSend(QUEUE, message);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void fallbackThrowsMessagingUnavailableException() throws Exception {
        StockReservationMessage message = new StockReservationMessage(1L, 3, "order-2");
        Throwable reason = new AmqpException("Broker unavailable");
        // Invoke the private fallback directly to verify the fallback exception contract.
        Method fallback = OrderMessagePublisher.class.getDeclaredMethod(
                "publishFallback", StockReservationMessage.class, Throwable.class);
        fallback.setAccessible(true);

        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> fallback.invoke(publisher, message, reason));

        MessagingUnavailableException failure = assertInstanceOf(
                MessagingUnavailableException.class, wrapper.getCause());
        assertEquals(message.orderId(), failure.getOrderId());
        assertTrue(failure.getMessage().contains(message.orderId()));
        assertSame(reason, failure.getCause());
        verifyNoInteractions(rabbitTemplate);
    }
}
