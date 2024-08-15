package br.com.microservices.orchestrated.orchestratorservice.core.service;

import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.History;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopic;
import br.com.microservices.orchestrated.orchestratorservice.core.producer.SagaOrchestratorProducer;
import br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaExecutionController;
import br.com.microservices.orchestrated.orchestratorservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class OrchestratorService {

    private final JsonUtil jsonUtil;
    private final SagaOrchestratorProducer sagaOrchestratorProducer;
    private final SagaExecutionController sagaExecutionController;

    public void startSaga(Event event) {

        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        var topic = getTopic(event);
        log.info("SAGA STARTED!");
        this.addHistory(event, "Saga started!");
        this.sendToOrchestratorProducerWithTopic(event, topic);
    }

    public void finishSagaSuccess(Event event) {

        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        log.info("SAGA FINISHED SUCCESSFULLY! ".concat(event.getId()));
        this.addHistory(event, "Saga finished successfully!");
        this.notifyFinishedSaga(event);
    }

    public void finishSagaFail(Event event) {

        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.FAIL);
        log.info("SAGA FINISHED WITH ERRORS FOR EVENT {} ".concat(event.getId()));
        this.addHistory(event, "Saga finished with errors!!");
        this.notifyFinishedSaga(event);
    }

    public void continueSaga(Event event) {

        var topic = this.getTopic(event);
        log.info("SAGA CONTINUING FOR EVENT {} ".concat(event.getId()));
        this.sendToOrchestratorProducerWithTopic(event, topic);
    }

    private ETopic getTopic(Event event) {
        return this.sagaExecutionController.getNextTopic(event);
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

    private void notifyFinishedSaga(Event event) {
        this.sagaOrchestratorProducer.sendEvent(this.jsonUtil.toJson(event), ETopic.NOTIFY_ENDING.getTopic());
    }

    private void sendToOrchestratorProducerWithTopic(Event event, ETopic topic) {
        this.sagaOrchestratorProducer.sendEvent(jsonUtil.toJson(event), topic.getTopic());
    }
}
