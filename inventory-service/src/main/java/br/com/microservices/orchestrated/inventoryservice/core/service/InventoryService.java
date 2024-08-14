package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Order;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.InventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.OrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

        } catch (Exception e) {
            log.error("Error trying to update inventory", e);
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

    private Inventory findInventoryByProductCode(String productCode) {
        return inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Inventory not found by informed product: ".concat(productCode)));
    }

}
