package com.example.product.messaging;

import com.example.product.mapper.ProductMapper;
import com.example.product.model.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockReservationListenerTest {
    @Mock
    private ProductRepository repository;

    @Spy
    private ProductMapper mapper = new ProductMapper();

    @InjectMocks
    private ProductServiceImpl service;

    private StockReservationListener listener;

    @BeforeEach
    void setUp() {
        // Real listener -> real service -> mocked repository; no Spring context.
        listener = new StockReservationListener(service);
    }

    @Test
    void sufficientStockSavesReducedStock() {
        Product product = productWithStock(10);
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        StockReservationMessage message = new StockReservationMessage(1L, 3, "order-1");

        assertDoesNotThrow(() -> listener.onMessage(message));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(repository, times(1)).save(saved.capture());
        assertEquals(7, saved.getValue().getStock());
        assertEquals(Long.valueOf(1L), saved.getValue().getId());
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void insufficientStockDoesNotSaveOrThrow() {
        Product product = productWithStock(2);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        assertDoesNotThrow(() -> listener.onMessage(new StockReservationMessage(1L, 3, "order-2")));

        verify(repository).findById(1L);
        verify(repository, never()).save(any(Product.class));
        assertEquals(2, product.getStock());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingProductDoesNotSaveOrThrow() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> listener.onMessage(new StockReservationMessage(99L, 1, "order-3")));

        verify(repository).findById(99L);
        verify(repository, never()).save(any(Product.class));
        verifyNoMoreInteractions(repository);
    }

    private Product productWithStock(int stock) {
        Product product = new Product();
        product.setId(1L);
        product.setStock(stock);
        return product;
    }
}
