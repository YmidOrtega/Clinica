package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class AttentionSpecifications {

    public static Specification<Attention> hasPatientId(Long patientId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("patientId"), patientId);
    }

    public static Specification<Attention> hasDoctorId(Long doctorId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("doctorId"), doctorId);
    }

    public static Specification<Attention> hasHealthProviderNit(String healthProviderNit) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isMember(healthProviderNit, root.get("healthProviderNit"));
    }

    public static Specification<Attention> hasStatus(AttentionStatus status) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Attention> hasCause(Cause cause) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("cause"), cause);
    }

    public static Specification<Attention> hasEntryMethod(String entryMethod) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("entryMethod"), entryMethod);
    }

    public static Specification<Attention> isReferral(Boolean isReferral) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("referred"), isReferral);
    }

    public static Specification<Attention> hasTriageLevel(TriageLevel triageLevel) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("triageLevel"), triageLevel);
    }

    public static Specification<Attention> hasAdmissionDateAfter(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("admissionDateTime"), date.atStartOfDay());
    }

    public static Specification<Attention> hasAdmissionDateBefore(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("admissionDateTime"), date.atTime(23, 59, 59));
    }

    public static Specification<Attention> hasDischargeDateAfter(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("dischargeDateTime"), date.atStartOfDay());
    }

    public static Specification<Attention> hasDischargeDateBefore(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("dischargeDateTime"), date.atTime(23, 59, 59));
    }

    public static Specification<Attention> isInvoiced(Boolean invoiced) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("invoiced"), invoiced);
    }

    public static Specification<Attention> hasConfigurationServiceId(Long configServiceId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("configurationService").get("id"), configServiceId);
    }
}