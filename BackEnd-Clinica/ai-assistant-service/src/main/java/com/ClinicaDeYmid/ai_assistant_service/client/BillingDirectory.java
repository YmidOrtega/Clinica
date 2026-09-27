package com.ClinicaDeYmid.ai_assistant_service.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
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

    public BillingLookup perform(BillingAction action) {
        Function<UUID, String> call = switch (action.kind()) {
            case SIGN -> client::sign;
            case SEND_TO_DIAN -> client::sendToDian;
            case VALIDATE_RIPS -> client::validateRips;
            case FILE -> invoice -> client.file(invoice, action.body());
            case ANSWER_OBJECTION -> invoice -> client.answerObjection(action.targetUuid(),
                    "\"" + action.targetVersion() + "\"", action.body());
        };
        return circuitBreaker.run(() -> lookup(call, action.invoiceUuid()), failure -> {
            log.warn("billing-service did not answer the {} request ({})", action.kind(),
                    failure.getClass().getSimpleName());
            return new BillingLookup.Unavailable();
        });
    }

    public OptionalLong objectionVersion(UUID objectionUuid) {
        return circuitBreaker.run(() -> {
            try {
                ResponseEntity<String> objection = client.objection(objectionUuid);
                String tag = objection.getHeaders().getETag();
                return tag == null ? OptionalLong.empty()
                        : OptionalLong.of(Long.parseLong(tag.replace("W/", "").replace("\"", "")));
            } catch (FeignException.FeignClientException | NumberFormatException unusable) {
                return OptionalLong.empty();
            }
        }, failure -> OptionalLong.empty());
    }

    private static BillingLookup lookup(Function<UUID, String> call, UUID invoiceUuid) {
        try {
            return new BillingLookup.Found(call.apply(invoiceUuid));
        } catch (FeignException.NotFound missing) {
            return new BillingLookup.NotFound();
        } catch (FeignException.Forbidden refused) {
            return new BillingLookup.Forbidden();
        } catch (FeignException.FeignClientException rejected) {
            return new BillingLookup.Refused(rejected.status(), rejected.contentUTF8());
        }
    }
}
