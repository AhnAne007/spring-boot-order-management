package com.example.order.messaging;

public record StockReservationMessage(Long productId, int quantity, String orderId) {}
