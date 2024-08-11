package br.com.microservices.orchestrated.orderservice.core.service;

import br.com.microservices.orchestrated.orderservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.dto.EventFilters;
import br.com.microservices.orchestrated.orderservice.core.repository.EventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static io.micrometer.common.util.StringUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public void notifyEnding(Event event) {

        event.setOrderId(event.getOrderId());
        event.setCreatedAt(LocalDateTime.now());

        save(event);

        log.info("Order {} with Saga notified! TransactionID: {}", event.getOrderId(), event.getTransactionId());
    }

    public List<Event> findAll() {

        return eventRepository.findAllByOrderByCreatedAtDesc();
    }

    public Event findByFilters(EventFilters filters) {

        this.validateEmptyFilters(filters);

        if (!isEmpty(filters.getOrderId()))
            return this.findByOrderId(filters.getOrderId());
        else
            return this.findByTransactionId(filters.getTransactionId());
    }

    private Event findByOrderId(String orderId) {

        return this.eventRepository.findTop1ByOrderIdOrderByCreatedAtDesc(orderId).orElseThrow(() -> new ValidationException("Event not found by orderID."));
    }

    private Event findByTransactionId(String transactionId) {

        return this.eventRepository.findTop1ByTransactionIdOrderByCreatedAtDesc(transactionId).orElseThrow(() -> new ValidationException("Event not found by transactionID."));
    }

    private void validateEmptyFilters(EventFilters filters) {

        if (isEmpty(filters.getOrderId()) && isEmpty(filters.getTransactionId()))
            throw new ValidationException("OrderID or TransactionID must be informed.");
    }

    public Event save(Event event) {

        return this.eventRepository.save(event);
    }
}
