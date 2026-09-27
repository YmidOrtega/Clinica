package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.CoveragePlan;
import com.ClinicaDeYmid.billing_service.domain.UncontractedCare;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UncontractedProposals {

    private final PatientDirectory patients;

    public UncontractedProposals(PatientDirectory patients) {
        this.patients = patients;
    }

    public record Proposal(UUID payerUuid, UncontractedCare reason, CoveragePlan coverage) {
    }

    public Optional<Proposal> of(AccountSummaries.Context context) {
        if (!InvoiceCommands.payerWithoutContract(context.episode().coverage())) {
            return Optional.empty();
        }
        UncontractedCare reason = context.summary().account().kind() == AdmissionKind.EMERGENCY
                ? UncontractedCare.EMERGENCY : null;
        return Optional.of(new Proposal(context.episode().coverage().payerUuid(), reason,
                coverageOf(context.summary().account().patientUuid())));
    }

    private CoveragePlan coverageOf(UUID patientUuid) {
        String regime = switch (patients.patient(patientUuid)) {
            case PatientLookup.Found found -> found.patient() instanceof PatientDetails.Registered registered
                    ? registered.healthRegime() : null;
            case PatientLookup.NotFound ignored -> null;
            case PatientLookup.Unavailable ignored -> null;
        };
        if (regime == null) {
            return null;
        }
        return switch (regime) {
            case "CONTRIBUTORY" -> CoveragePlan.UPC_CONTRIBUTORY;
            case "SUBSIDIZED" -> CoveragePlan.UPC_SUBSIDIZED;
            default -> null;
        };
    }
}
