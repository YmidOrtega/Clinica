package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.repository.FeeAgreementRepository;
import com.ClinicaDeYmid.practitioners_service.repository.PractitionerRepository;
import com.ClinicaDeYmid.practitioners_service.repository.entity.FeeAgreement;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import com.ClinicaDeYmid.practitioners_service.service.FeeCommands.NewAgreement;
import com.ClinicaDeYmid.practitioners_service.service.FeeViews.AgreementView;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeeService {

    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    private final FeeAgreementRepository agreements;
    private final PractitionerRepository practitioners;
    private final Clock clock;

    public FeeService(FeeAgreementRepository agreements, PractitionerRepository practitioners, Clock clock) {
        this.agreements = agreements;
        this.practitioners = practitioners;
        this.clock = clock;
    }

    @Transactional
    public AgreementView agree(UUID practitionerUuid, NewAgreement command) {
        Practitioner practitioner = practitioners.findByUuid(practitionerUuid)
                .orElseThrow(PractitionersException.PractitionerNotFound::new);
        FeeAgreement agreement = FeeAgreement.agree(practitioner, command.basis(), command.amount(),
                command.procedures().stream()
                        .map(procedure -> new FeeAgreement.Line(procedure.serviceCode(), procedure.amount()))
                        .toList(),
                command.validFrom(), command.note());
        agreements.standing(practitionerUuid).forEach(standing -> standing.revokeFrom(agreement.validFrom()));
        FeeAgreement agreed = agreements.saveAndFlush(agreement);
        log.info("Fees agreed: practitioner={} agreement={} basis={} from={}", practitionerUuid, agreed.uuid(),
                agreed.basis(), agreed.validFrom());
        return AgreementView.of(agreed, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<AgreementView> history(UUID practitionerUuid) {
        practitioners.findByUuid(practitionerUuid).orElseThrow(PractitionersException.PractitionerNotFound::new);
        LocalDate today = LocalDate.now(clock);
        return agreements.ofPractitioner(practitionerUuid).stream()
                .map(agreement -> AgreementView.of(agreement, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AgreementView> inForceOn(UUID practitionerUuid, LocalDate on) {
        practitioners.findByUuid(practitionerUuid).orElseThrow(PractitionersException.PractitionerNotFound::new);
        LocalDate date = on == null ? LocalDate.now(clock) : on;
        return agreements.inForceOn(practitionerUuid, date).map(agreement -> AgreementView.of(agreement, date));
    }
}
