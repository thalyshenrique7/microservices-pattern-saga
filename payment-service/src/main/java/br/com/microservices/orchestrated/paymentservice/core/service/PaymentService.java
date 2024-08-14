package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static final double

    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final PaymentRepository paymentRepository;

    public void realizePayment(Event event) {

        try {
            this.checkCurrentValidation(event);
            this.createPendingPayment(event);

        } catch (Exception e) {
            log.error("Error trying to make payment", e);
        }

        kafkaProducer.sendEvent(this.jsonUtil.toJson(event));
    }

    private void createPendingPayment(Event event) {

        var totalAmount = this.calculateAmount(event);
        var totalItems = this.calculateTotalItems(event);

        var payment = Payment
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();

        this.save(payment);
        this.setEventAmountItems(event, payment);
    }

    private void checkCurrentValidation(Event event) {

        if (paymentRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getPayload().getTransactionId()))
            throw new ValidationException("There's another transactionID for this validation.");
    }

    private double calculateAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(product -> product.getQuantity() * product.getProduct().getUnitValue())
                .reduce(0.0, Double::sum);
    }

    private int calculateTotalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProducts::getQuantity)
                .reduce(0, Integer::sum);
    }

    private void setEventAmountItems(Event event, Payment payment) {

        payment.setTotalAmount(payment.getTotalAmount());
        payment.setTotalItems(payment.getTotalItems());
    }

    private void save(Payment payment) {
        this.paymentRepository.save(payment);
    }
}
