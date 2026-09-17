package com.example.product.service.impl;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;
import com.example.product.service.interfaces.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
@Service
public class ProductServiceImpl implements ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);
    private final ProductRepository repository;
    private final ProductMapper mapper;
    public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    @Override @Transactional
    public ProductResponse create(CreateProductRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Override @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return mapper.toResponse(repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id)));
    }
    @Override @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
    // Transaction is owned by the listener for this workflow.
    @Override
    public void reserveStock(Long productId, int quantity, String orderId) {
        if (productId == null || quantity < 1) {
            log.warn("Invalid reservation orderId={} productId={} quantity={}", orderId, productId, quantity);
            return;
        }
        var found = repository.findById(productId);
        if (found.isEmpty()) {
            log.warn("Product not found: productId={} orderId={}", productId, orderId);
            return;
        }
        var product = found.get();
        if (product.getStock() < quantity) {
            log.warn("Insufficient stock: available={} requested={} productId={} orderId={}",
                    product.getStock(), quantity, productId, orderId);
            return;
        }
        product.setStock(product.getStock() - quantity);
        repository.save(product);
        log.info("Stock reserved: productId={} quantity={} remaining={} orderId={}",
                productId, quantity, product.getStock(), orderId);
    }
}
