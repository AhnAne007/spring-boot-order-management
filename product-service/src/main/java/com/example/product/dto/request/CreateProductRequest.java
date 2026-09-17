package com.example.product.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record CreateProductRequest(@NotBlank String name, @NotNull @Positive BigDecimal price,
                                   @Min(0) int stock) {}
