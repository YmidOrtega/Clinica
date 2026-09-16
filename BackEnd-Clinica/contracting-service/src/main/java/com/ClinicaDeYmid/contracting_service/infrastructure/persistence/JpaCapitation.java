package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.Capitation;
import com.ClinicaDeYmid.contracting_service.domain.CapitatedMember;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import com.ClinicaDeYmid.contracting_service.domain.MemberVerification;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCapitation implements Capitation {

    private final FundingAgreementJpaRepository agreements;
    private final CapitatedMemberJpaRepository members;

    JpaCapitation(FundingAgreementJpaRepository agreements, CapitatedMemberJpaRepository members) {
        this.agreements = agreements;
        this.members = members;
    }

    @Override
    public FundingAgreement save(FundingAgreement agreement) {
        return agreements.saveAndFlush(agreement);
    }

    @Override
    public Optional<FundingAgreement> findAgreementByUuid(UUID uuid) {
        return agreements.findByUuid(uuid);
    }

    @Override
    public List<FundingAgreement> agreementsOf(UUID contractUuid) {
        return agreements.findByContract(contractUuid);
    }

    @Override
    public Optional<FundingAgreement> agreementInForce(UUID contractUuid, LocalDate date) {
        return agreements.findApplying(contractUuid, date).stream().findFirst();
    }

    @Override
    public List<CapitatedMember> saveMembers(List<CapitatedMember> entries) {
        return members.saveAll(entries);
    }

    @Override
    public CapitatedMember save(CapitatedMember member) {
        return members.saveAndFlush(member);
    }

    @Override
    public Optional<CapitatedMember> findMember(UUID contractUuid, YearMonth period, String documentType, String documentNumber) {
        return members.findMember(contractUuid, period.atDay(1), documentType, documentNumber);
    }

    @Override
    public List<CapitatedMember> membersOf(UUID contractUuid, YearMonth period, int page, int size) {
        return members.findByPeriod(contractUuid, period.atDay(1), PageRequest.of(page, size));
    }

    @Override
    public List<CapitatedMember> unverifiedMembers(UUID contractUuid, YearMonth period, int limit) {
        return members.findByVerification(contractUuid, period.atDay(1), MemberVerification.UNVERIFIED,
                PageRequest.of(0, Math.max(limit, 1)));
    }

    @Override
    public long countMembers(UUID contractUuid, YearMonth period) {
        return members.countByPeriod(contractUuid, period.atDay(1));
    }

    @Override
    public List<CapitatedMember> coverageOf(String documentType, String documentNumber, YearMonth period) {
        return members.findCoverage(documentType, documentNumber, period.atDay(1));
    }
}
