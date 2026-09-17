package com.example.product.controller;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.interfaces.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) { return service.findById(id); }
    @GetMapping
    public List<ProductResponse> findAll() { return service.findAll(); }
}
