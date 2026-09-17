package com.example.order.dto.response;

public record OrderResponse(Long id, Long productId, int quantity, String orderId, String status) {}
