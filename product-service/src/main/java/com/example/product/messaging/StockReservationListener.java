package com.example.product.messaging;

import com.example.product.service.interfaces.ProductService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.example.product.config.RabbitConfig.QUEUE;
@Component
public class StockReservationListener {
    private static final Logger log = LoggerFactory.getLogger(StockReservationListener.class);
    private final ProductService service;
    public StockReservationListener(ProductService service) { this.service = service; }
    @RabbitListener(queues = QUEUE)
    @Transactional
    public void onMessage(StockReservationMessage msg) {
        try {
            service.reserveStock(msg.productId(), msg.quantity(), msg.orderId());
        } catch (RuntimeException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Reservation processing failed; rolling back without requeue: {}", msg, ex);
        }
    }
}
