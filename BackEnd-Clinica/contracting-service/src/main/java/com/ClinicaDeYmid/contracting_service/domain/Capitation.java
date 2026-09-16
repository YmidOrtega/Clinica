package com.ClinicaDeYmid.contracting_service.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Capitation {

    FundingAgreement save(FundingAgreement agreement);

    Optional<FundingAgreement> findAgreementByUuid(UUID uuid);

    List<FundingAgreement> agreementsOf(UUID contractUuid);

    Optional<FundingAgreement> agreementInForce(UUID contractUuid, LocalDate date);

    List<CapitatedMember> saveMembers(List<CapitatedMember> members);

    CapitatedMember save(CapitatedMember member);

    Optional<CapitatedMember> findMember(UUID contractUuid, YearMonth period, String documentType, String documentNumber);

    List<CapitatedMember> membersOf(UUID contractUuid, YearMonth period, int page, int size);

    List<CapitatedMember> unverifiedMembers(UUID contractUuid, YearMonth period, int limit);

    long countMembers(UUID contractUuid, YearMonth period);

    List<CapitatedMember> coverageOf(String documentType, String documentNumber, YearMonth period);
}
