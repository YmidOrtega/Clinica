package com.ClinicaDeYmid.ai_assistant_service.client;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Function;

@Component
public class BillingDirectory {

    public enum Aspect {
        INVOICE,
        DIAN_VERDICTS,
        RIPS_VALIDATIONS,
        FILING,
        OBJECTIONS,
        CREDIT_NOTES
    }

    static final String CIRCUIT_BREAKER = "billing-service";

    private static final Logger log = LoggerFactory.getLogger(BillingDirectory.class);

    private final BillingClient client;
    private final CircuitBreaker circuitBreaker;

    BillingDirectory(BillingClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    public BillingLookup read(Aspect aspect, UUID invoiceUuid) {
        Function<UUID, String> call = switch (aspect) {
            case INVOICE -> client::invoice;
            case DIAN_VERDICTS -> client::dianVerdicts;
            case RIPS_VALIDATIONS -> client::ripsValidations;
            case FILING -> client::filing;
            case OBJECTIONS -> client::objections;
            case CREDIT_NOTES -> client::creditNotes;
        };
        return circuitBreaker.run(() -> lookup(call, invoiceUuid), failure -> {
            log.warn("billing-service did not answer ({})", failure.getClass().getSimpleName());
            return new BillingLookup.Unavailable();
        });
    }

    public BillingLookup perform(ActionKind kind, UUID invoiceUuid) {
        Function<UUID, String> call = switch (kind) {
            case SIGN -> client::sign;
            case SEND_TO_DIAN -> client::sendToDian;
            case VALIDATE_RIPS -> client::validateRips;
        };
        return circuitBreaker.run(() -> lookup(call, invoiceUuid), failure -> {
            log.warn("billing-service did not answer the {} request ({})", kind, failure.getClass().getSimpleName());
            return new BillingLookup.Unavailable();
        });
    }

    private static BillingLookup lookup(Function<UUID, String> call, UUID invoiceUuid) {
        try {
            return new BillingLookup.Found(call.apply(invoiceUuid));
        } catch (FeignException.NotFound missing) {
            return new BillingLookup.NotFound();
        } catch (FeignException.Forbidden | FeignException.Unauthorized refused) {
            return new BillingLookup.Forbidden();
        } catch (FeignException.FeignClientException rejected) {
            return new BillingLookup.Refused(rejected.status(), rejected.contentUTF8());
        }
    }
}
