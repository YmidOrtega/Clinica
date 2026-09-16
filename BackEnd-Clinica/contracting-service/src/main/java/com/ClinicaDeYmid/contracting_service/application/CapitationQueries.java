package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.Capitation;
import com.ClinicaDeYmid.contracting_service.domain.CapitatedMember;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CapitationQueries {

    private final Capitation capitation;

    public CapitationQueries(Capitation capitation) {
        this.capitation = capitation;
    }

    public List<FundingAgreement> agreementsOf(UUID contractUuid) {
        return capitation.agreementsOf(contractUuid);
    }

    public FundingAgreement agreementInForce(UUID contractUuid, LocalDate date) {
        return capitation.agreementInForce(contractUuid, date)
                .orElseThrow(ContractingException.FundingAgreementNotFound::new);
    }

    public Page members(UUID contractUuid, YearMonth period, int page, int size) {
        return new Page(capitation.membersOf(contractUuid, period, page, size),
                capitation.countMembers(contractUuid, period));
    }

    public List<CapitatedMember> coverage(String documentType, String documentNumber, YearMonth period) {
        return capitation.coverageOf(documentType.toUpperCase(), documentNumber.toUpperCase(), period);
    }

    public record Page(List<CapitatedMember> members, long total) {
    }
}
