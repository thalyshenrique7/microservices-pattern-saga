package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentsStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static final Double REDUCE_SUM_VALUE = 0.0;
    private static final Double MIN_AMOUNT_VALUE = 0.1;


    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final PaymentRepository paymentRepository;

    public void realizePayment(Event event) {

        try {
            this.checkCurrentValidation(event);
            this.createPendingPayment(event);
            var payment = this.findByOrderIdAndTransactionId(event);
            this.validateAmount(payment.getTotalAmount());
            this.changePaymentToSuccess(payment);
            this.handleSuccess(event);
        } catch (Exception e) {
            log.error("Error trying to make payment", e);
            this.handleFailCurrentNotExecute(event, e.getMessage());
        }

        kafkaProducer.sendEvent(this.jsonUtil.toJson(event));
    }

    public void realizeRefund(Event event) {

        this.changePaymentStatusToRefund(event);
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Rollback executed for payment!");
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
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
                .reduce(REDUCE_SUM_VALUE, Double::sum);
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

    private void validateAmount(double amount) {

        if (amount < MIN_AMOUNT_VALUE)
            throw new ValidationException("The minimum amount available is ".concat(MIN_AMOUNT_VALUE.toString()));
    }

    private void changePaymentToSuccess(Payment payment) {

        payment.setPaymentStatus(EPaymentsStatus.SUCCESS);
        this.save(payment);
    }

    private void handleSuccess(Event event) {

        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        this.addHistory(event, "Payment realized successfully.");
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

        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to realized payment: ".concat(message));
    }

    private void changePaymentStatusToRefund(Event event) {
        var payment = this.findByOrderIdAndTransactionId(event);
        payment.setPaymentStatus(EPaymentsStatus.REFUND);
        this.setEventAmountItems(event, payment);
        this.save(payment);
    }

    private void save(Payment payment) {
        this.paymentRepository.save(payment);
    }

    private Payment findByOrderIdAndTransactionId(Event event) {

        return this.paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found by OrderID and TransactionID!"));
    }
}
