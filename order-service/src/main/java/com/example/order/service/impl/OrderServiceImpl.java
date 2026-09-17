package com.example.order.service.impl;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.mapper.OrderMapper;
import com.example.order.exception.MessagingUnavailableException;
import com.example.order.messaging.OrderMessagePublisher;
import com.example.order.repository.OrderRepository;
import com.example.order.service.interfaces.OrderService;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final OrderMessagePublisher publisher;
    public OrderServiceImpl(OrderRepository repository, OrderMapper mapper, OrderMessagePublisher publisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
    }
    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        // The repository transaction commits before publication; no distributed transaction is claimed.
        var order = repository.save(mapper.toEntity(request, UUID.randomUUID().toString()));
        try {
            // Different Spring bean: the Resilience4j AOP proxy intercepts this call.
            publisher.publish(mapper.toMessage(order));
        } catch (MessagingUnavailableException ex) {
            order.setStatus("FAILED");
            // Each repository.save commits independently. Do not wrap createOrder
            // in a rollback-on-exception transaction or this update would be lost.
            repository.save(order);
            throw ex;
        }
        return mapper.toResponse(order);
    }
}
