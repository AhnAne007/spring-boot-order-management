package com.example.order.service.interfaces;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
}
