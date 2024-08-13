package br.com.microservices.orchestrated.productvalidationservice.core.service;

import br.com.microservices.orchestrated.productvalidationservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.Event;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.History;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.productvalidationservice.core.model.Validation;
import br.com.microservices.orchestrated.productvalidationservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class ProductValidationService {

    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final ProductRepository productRepository;
    private final ValidationRepository validationRepository;

    public void validateExistingProducts(Event event) {

        try {
            checkCurrentValidation(event);
            createValidation(event, true);
            handleSuccess(event);
        } catch (Exception e) {
            log.error("Error trying to validate products: ", e);
            handleFailCurrentNotExecute(event, e.getMessage());
        }

        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void validateProductsInformed(Event event) {

        if (isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts()))
            throw new ValidationException("Product list is empty.");

        if (isEmpty(event.getPayload().getId()) || isEmpty(event.getPayload().getTransactionId()))
            throw new ValidationException("OrderID or TransactionID must be informed!");
    }

    private void checkCurrentValidation(Event event) {

        this.validateProductsInformed(event);

        if (validationRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getPayload().getTransactionId()))
            throw new ValidationException("There's another transactionID for this validation.");

        event.getPayload().getProducts().forEach(product -> {
            this.validateProductInformed(product);
            this.validateExistingProduct(product.getProduct().getCode());
        });
    }

    private void validateProductInformed(OrderProducts orderProducts) {

        if (isEmpty(orderProducts.getProduct()) || isEmpty(orderProducts.getProduct().getCode()))
            throw new ValidationException("Product must be informed!");
    }

    private void validateExistingProduct(String code) {

        if (productRepository.existsByCode(code))
            throw new ValidationException("Product does not exists in database!");
    }

    private void createValidation(Event event, boolean success) {
        var validation = Validation
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .success(success)
                .build();
        validationRepository.save(validation);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Product successfully validated.");
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

    private void handleFailCurrentNotExecute(Event event, String message) {

    }
}
