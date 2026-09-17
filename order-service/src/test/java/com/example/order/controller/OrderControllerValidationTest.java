package com.example.order.controller;

import com.example.order.exception.GlobalExceptionHandler;
import com.example.order.service.interfaces.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerValidationTest {
    @Mock private OrderService service;
    @InjectMocks private OrderController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Standalone MVC infrastructure, not a Spring Boot application context.
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingProductIdReturns400() throws Exception {
        assertBadRequest("{\"quantity\":1}");
    }

    @Test
    void zeroQuantityReturns400() throws Exception {
        assertBadRequest("{\"productId\":1,\"quantity\":0}");
    }

    @Test
    void negativeQuantityReturns400() throws Exception {
        assertBadRequest("{\"productId\":1,\"quantity\":-1}");
    }

    private void assertBadRequest(String body) throws Exception {
        mvc.perform(post("/orders").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
