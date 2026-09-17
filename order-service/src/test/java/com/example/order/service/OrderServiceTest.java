package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.mapper.OrderMapper;
import com.example.order.messaging.OrderMessagePublisher;
import com.example.order.messaging.StockReservationMessage;
import com.example.order.model.entity.Order;
import com.example.order.repository.OrderRepository;
import com.example.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository repository;

    @Mock
    private OrderMessagePublisher publisher;

    @Spy
    private OrderMapper mapper = new OrderMapper();

    @InjectMocks
    private OrderServiceImpl service;

    @Test
    void createOrderSavesOrderThenDelegatesToPublisher() {
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42L);
            return order;
        });

        var response = service.createOrder(new CreateOrderRequest(5L, 3));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        ArgumentCaptor<StockReservationMessage> published = ArgumentCaptor.forClass(StockReservationMessage.class);
        var sequence = inOrder(repository, publisher);
        sequence.verify(repository, times(1)).save(saved.capture());
        sequence.verify(publisher, times(1)).publish(published.capture());
        verifyNoMoreInteractions(repository, publisher);

        Order order = saved.getValue();
        assertEquals(Long.valueOf(5L), order.getProductId());
        assertEquals(3, order.getQuantity());
        assertEquals("PENDING", order.getStatus());
        assertNotNull(order.getOrderId());
        assertDoesNotThrow(() -> UUID.fromString(order.getOrderId()));
        assertEquals(new StockReservationMessage(5L, 3, order.getOrderId()), published.getValue());
        assertEquals(Long.valueOf(42L), response.id());
        assertEquals(Long.valueOf(5L), response.productId());
        assertEquals(3, response.quantity());
        assertEquals(order.getOrderId(), response.orderId());
        assertEquals("PENDING", response.status());
    }
}
