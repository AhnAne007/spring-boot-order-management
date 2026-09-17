package com.example.product.messaging;

public record StockReservationMessage(Long productId, int quantity, String orderId) {}
