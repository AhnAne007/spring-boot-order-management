package com.example.order.mapper;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.messaging.StockReservationMessage;
import com.example.order.model.entity.Order;
import org.springframework.stereotype.Component;
@Component
public class OrderMapper {
    public Order toEntity(CreateOrderRequest request, String orderId) {
        Order order = new Order();
        order.setProductId(request.productId());
        order.setQuantity(request.quantity());
        order.setOrderId(orderId);
        order.setStatus("PENDING");
        return order;
    }
    public StockReservationMessage toMessage(Order order) {
        return new StockReservationMessage(order.getProductId(), order.getQuantity(), order.getOrderId());
    }
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getProductId(), order.getQuantity(), order.getOrderId(), order.getStatus());
    }
}
