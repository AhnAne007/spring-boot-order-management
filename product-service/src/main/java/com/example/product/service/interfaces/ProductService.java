package com.example.product.service.interfaces;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.response.ProductResponse;
import java.util.List;
public interface ProductService {
    ProductResponse create(CreateProductRequest request);
    ProductResponse findById(Long id);
    List<ProductResponse> findAll();
    void reserveStock(Long productId, int quantity, String orderId);
}
