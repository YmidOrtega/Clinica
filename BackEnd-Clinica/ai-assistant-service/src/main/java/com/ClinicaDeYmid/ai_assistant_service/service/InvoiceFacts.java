package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record InvoiceFacts(UUID invoiceUuid, String number, String purpose, String status, LocalDate issuedOn,
                    String buyerKind, String uncontractedCare, BigDecimal shareShortfall, String dianStatus,
                    Instant dianStatusAt, Instant lastEventAt, String cuv, boolean filed,
                    List<CreditNoteFact> creditNotes, List<ObjectionFact> objections) {

    private static final ObjectMapper JSON = new ObjectMapper();

    record CreditNoteFact(String uuid, String number, String dianStatus) {
    }

    record ObjectionFact(String uuid, String kind, String payerRecord, String status) {
    }

    static InvoiceFacts of(InvoiceSnapshot snapshot) {
        JsonNode state = parse(snapshot.state());
        List<CreditNoteFact> notes = new ArrayList<>();
        state.path("creditNotes").forEach(note -> notes.add(new CreditNoteFact(note.path("uuid").asText(),
                note.path("number").asText(), note.hasNonNull("dianStatus") ? note.get("dianStatus").asText() : null)));
        List<ObjectionFact> objections = new ArrayList<>();
        state.path("objections").forEach(objection -> objections.add(new ObjectionFact(
                objection.path("uuid").asText(), objection.path("kind").asText(),
                objection.path("payerRecord").asText(), objection.path("status").asText())));
        return new InvoiceFacts(snapshot.invoiceUuid(), snapshot.number(), snapshot.purpose(), snapshot.status(),
                snapshot.issuedOn(), snapshot.buyerKind(), snapshot.uncontractedCare(), snapshot.shareShortfall(),
                snapshot.dianStatus(), snapshot.dianStatusAt(), snapshot.lastEventAt(), snapshot.cuv(),
                snapshot.filingNumber() != null, List.copyOf(notes), List.copyOf(objections));
    }

    boolean voided() {
        return "VOIDED".equals(status);
    }

    boolean servicesToAPayer() {
        return "SERVICES".equals(purpose) && "PAYER".equals(buyerKind);
    }

    boolean awaitingAnswer(String objectionUuid) {
        return objections.stream().anyMatch(objection -> objection.uuid().equals(objectionUuid)
                && "AWAITING_RESPONSE".equals(objection.status()));
    }

    private static JsonNode parse(String state) {
        try {
            return JSON.readTree(state);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("The stored invoice state is not JSON", unreadable);
        }
    }
}
