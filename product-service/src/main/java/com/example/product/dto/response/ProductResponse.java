package com.example.product.dto.response;

import java.math.BigDecimal;
public record ProductResponse(Long id, String name, BigDecimal price, int stock) {}
