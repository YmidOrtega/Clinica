package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationType;
import com.ClinicaDeYmid.admissions_service.domain.Authorizations;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationCommands {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCommands.class);

    private final Authorizations authorizations;
    private final Admissions admissions;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AuthorizationCommands(Authorizations authorizations, Admissions admissions,
                                 TransactionOperations transactions, Clock clock) {
        this.authorizations = authorizations;
        this.admissions = admissions;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Authorization grant(UUID admissionUuid, String number, AuthorizationType type, String authorizedBy,
                               BigDecimal copayment, LocalDate validFrom, LocalDate validTo, Set<UUID> items) {
        return transactions.execute(status -> {
            Admission admission = admissions.findByUuid(admissionUuid)
                    .orElseThrow(AdmissionsException.AdmissionNotFound::new);
            authorizations.findByAdmissionAndNumber(admissionUuid, number).ifPresent(other -> {
                throw new AdmissionsException.AuthorizationNumberAlreadyUsed();
            });
            Authorization granted = authorizations.save(Authorization.grant(admission, number, type, authorizedBy,
                    copayment, validFrom, validTo, items));
            log.info("Authorization {} granted on admission {} ({})", number, admission.number(), type);
            return granted;
        });
    }

    public Authorization revoke(UUID uuid, long expectedVersion, String reason) {
        return transactions.execute(status -> {
            Authorization authorization = authorizations.findByUuid(uuid)
                    .orElseThrow(AdmissionsException.AuthorizationNotFound::new);
            if (authorization.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            authorization.revoke(reason, clock);
            return authorizations.save(authorization);
        });
    }

    public List<Authorization> ofAdmission(UUID admissionUuid) {
        return authorizations.findByAdmission(admissionUuid);
    }
}
