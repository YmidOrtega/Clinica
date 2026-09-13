package com.ClinicaDeYmid.commons.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ProblemDetails {

    public static final String CODE = "code";
    public static final String TRACE_ID = "traceId";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORS = "errors";

    private static final String TYPE_PREFIX = "urn:clinica:error:";
    private static final String TRACE_PREFIX = "urn:clinica:trace:";
    private static final String OCCURRENCE_PREFIX = "urn:uuid:";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return enrich(problem, code);
    }

    public static ProblemDetail withErrors(ProblemDetail problem, List<FieldViolation> errors) {
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    public static ProblemDetail enrich(ProblemDetail problem, String code) {
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty(CODE, code);
        problem.setProperty(TIMESTAMP, Instant.now().toString());
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null) {
            problem.setProperty(TRACE_ID, traceId);
            problem.setInstance(URI.create(TRACE_PREFIX + traceId));
        } else {
            problem.setInstance(URI.create(OCCURRENCE_PREFIX + UUID.randomUUID()));
        }
        return problem;
    }

    public record FieldViolation(String field, String message) {
    }
}
