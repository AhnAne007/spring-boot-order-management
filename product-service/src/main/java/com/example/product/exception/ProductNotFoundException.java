package com.example.product.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) { super("Product not found: " + id); }
}
