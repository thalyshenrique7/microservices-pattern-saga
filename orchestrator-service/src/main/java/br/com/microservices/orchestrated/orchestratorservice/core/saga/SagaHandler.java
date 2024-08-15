package br.com.microservices.orchestrated.orchestratorservice.core.saga;

import br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopic;

public class SagaHandler {

    private SagaHandler() {

    }

    public static final Object[][] SAGA_HANDLER = {
            { EEventSource.ORCHESTRATOR, ESagaStatus.SUCCESS, ETopic.PRODUCT_VALIDATION_SUCCESS },
            { EEventSource.ORCHESTRATOR, ESagaStatus.FAIL, ETopic.FINISH_FAIL },

            { EEventSource.PRODUCT_VALIDATION_SERVICE, ESagaStatus.ROLLBACK_PENDING, ETopic.PRODUCT_VALIDATION_FAIL },
            { EEventSource.PRODUCT_VALIDATION_SERVICE, ESagaStatus.FAIL, ETopic.FINISH_FAIL },
            { EEventSource.PRODUCT_VALIDATION_SERVICE, ESagaStatus.SUCCESS, ETopic.PAYMENT_SUCCESS },

            { EEventSource.PAYMENT_SERVICE, ESagaStatus.ROLLBACK_PENDING, ETopic.PAYMENT_FAIL },
            { EEventSource.PAYMENT_SERVICE, ESagaStatus.FAIL, ETopic.PRODUCT_VALIDATION_FAIL },
            { EEventSource.PAYMENT_SERVICE, ESagaStatus.SUCCESS, ETopic.INVENTORY_SUCCESS },

            { EEventSource.INVENTORY_SERVICE, ESagaStatus.ROLLBACK_PENDING, ETopic.INVENTORY_FAIL },
            { EEventSource.INVENTORY_SERVICE, ESagaStatus.FAIL, ETopic.PAYMENT_FAIL },
            { EEventSource.INVENTORY_SERVICE, ESagaStatus.SUCCESS, ETopic.FINISH_SUCCESS },
    };

    public static final int EVENT_SOURCE_INDEX = 0;
    public static final int SAGA_STATUS_INDEX = 0;
    public static final int TOPIC_INDEX = 0;
}
