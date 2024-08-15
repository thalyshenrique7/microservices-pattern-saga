package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.History;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Order;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.inventoryservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.InventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.OrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final InventoryRepository inventoryRepository;
    private final OrderInventoryRepository orderInventoryRepository;

    public void updateInventory(Event event) {

        try {

            this.checkCurrentValidation(event);
            this.createOrderInventory(event);
            this.updateInventory(event.getPayload());
            this.handleSuccess(event);

        } catch (Exception e) {
            log.error("Error trying to update inventory", e);
            this.handleFailCurrentNotExecute(event, e.getMessage());
        }

        kafkaProducer.sendEvent(this.jsonUtil.toJson(event));
    }

    private void checkCurrentValidation(Event event) {

        if (orderInventoryRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getPayload().getTransactionId()))
            throw new ValidationException("There's another transactionID for this validation.");
    }

    private void createOrderInventory(Event event) {
        event
                .getPayload()
                .getProducts()
                .forEach(product -> {
                    var inventory = this.findInventoryByProductCode(product.getProduct().getCode());
                    var orderInventory = this.createOrderInventory(event, product, inventory);
                    this.orderInventoryRepository.save(orderInventory);
                });
    }

    private OrderInventory createOrderInventory(Event event, OrderProducts orderProducts, Inventory inventory) {
        return OrderInventory
                .builder()
                .inventory(inventory)
                .oldQuantity(inventory.getAvailable())
                .orderQuantity(orderProducts.getQuantity())
                .newQuantity(inventory.getAvailable() - orderProducts.getQuantity())
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .build();
    }

    private void updateInventory(Order order) {

        order
                .getProducts()
                .forEach(product -> {
                    var inventory = this.findInventoryByProductCode(product.getProduct().getCode());
                    this.checkInventory(inventory.getAvailable(), product.getQuantity());
                    inventory.setAvailable(inventory.getAvailable() - product.getQuantity());
                    this.inventoryRepository.save(inventory);
                });
    }

    public void checkInventory(int available, int orderQuantity) {

        if (orderQuantity > available)
            throw new ValidationException("Product is out of stock!.");
    }

    private Inventory findInventoryByProductCode(String productCode) {
        return inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Inventory not found by informed product: ".concat(productCode)));
    }

    private void handleSuccess(Event event) {

        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        this.addHistory(event, "Inventory updated successfully.");
    }

    private void handleFailCurrentNotExecute(Event event, String message) {

        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to realized update inventory: ".concat(message));
    }

    public void rollbackInventory(Event event) {

        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);

        try {

            this.returnInventoryToPreviousValues(event);
            addHistory(event, "Rollback executed for inventory!");

        } catch (Exception e) {
            addHistory(event, "Rollback not executed for inventory!");
        }
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void returnInventoryToPreviousValues(Event event) {

        this.orderInventoryRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .forEach(orderInventory -> {
                    var inventory = orderInventory.getInventory();
                    inventory.setAvailable(orderInventory.getOldQuantity());
                    this.inventoryRepository.save(inventory);
                    log.info("Restored inventory for order {} from {} to {}",
                            event.getPayload().getId(), orderInventory.getNewQuantity(), inventory.getAvailable());
                });
    }

    private void addHistory(Event event, String message) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        event.addToHistory(history);
    }

}
