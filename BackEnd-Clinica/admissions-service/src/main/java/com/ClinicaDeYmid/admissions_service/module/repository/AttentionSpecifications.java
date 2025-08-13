package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.HealthProviderInfo;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttentionSpecifications {

    public static Specification<Attention> hasPatientId(Long patientId) {
        return (root, query, criteriaBuilder) ->
                patientId == null ? null : criteriaBuilder.equal(root.get("patientId"), patientId);
    }

    public static Specification<Attention> hasDoctorId(Long doctorId) {
        return (root, query, criteriaBuilder) ->
                doctorId == null ? null : criteriaBuilder.equal(root.get("doctorId"), doctorId);
    }

    public static Specification<Attention> hasHealthProviderNit(String nit) {
        return (root, query, criteriaBuilder) -> {
            if (nit == null || nit.trim().isEmpty()) {
                return null;
            }
            Join<Attention, HealthProviderInfo> healthProviderJoin = root.join("healthProviderNit");
            return criteriaBuilder.equal(healthProviderJoin.get("healthProviderNit"), nit);
        };
    }

    public static Specification<Attention> hasStatus(AttentionStatus status) {
        return (root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Attention> hasCause(Cause cause) {
        return (root, query, criteriaBuilder) ->
                cause == null ? null : criteriaBuilder.equal(root.get("cause"), cause);
    }

    public static Specification<Attention> hasEntryMethod(String entryMethod) {
        return (root, query, criteriaBuilder) ->
                entryMethod == null || entryMethod.trim().isEmpty() ? null :
                        criteriaBuilder.equal(root.get("entryMethod"), entryMethod);
    }

    public static Specification<Attention> isReferral(Boolean isReferral) {
        return (root, query, criteriaBuilder) ->
                isReferral == null ? null : criteriaBuilder.equal(root.get("isPreAdmission"), isReferral);
    }

    public static Specification<Attention> hasTriageLevel(TriageLevel triageLevel) {
        return (root, query, criteriaBuilder) ->
                triageLevel == null ? null : criteriaBuilder.equal(root.get("triageLevel"), triageLevel);
    }

    public static Specification<Attention> hasAdmissionDateAfter(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                date == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), date.atStartOfDay());
    }

    public static Specification<Attention> hasAdmissionDateBefore(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                date == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), date.atTime(23, 59, 59));
    }

    public static Specification<Attention> hasDischargeDateAfter(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                date == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("dischargeDateTime"), date.atStartOfDay());
    }

    public static Specification<Attention> hasDischargeDateBefore(LocalDate date) {
        return (root, query, criteriaBuilder) ->
                date == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("dischargeDateTime"), date.atTime(23, 59, 59));
    }

    public static Specification<Attention> hasDischargeDateAfter(LocalDateTime dateTime) {
        return (root, query, criteriaBuilder) ->
                dateTime == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("dischargeDateTime"), dateTime);
    }

    public static Specification<Attention> hasDischargeDateBefore(LocalDateTime dateTime) {
        return (root, query, criteriaBuilder) ->
                dateTime == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("dischargeDateTime"), dateTime);
    }

    public static Specification<Attention> isInvoiced(Boolean invoiced) {
        return (root, query, criteriaBuilder) ->
                invoiced == null ? null : criteriaBuilder.equal(root.get("invoiced"), invoiced);
    }

    public static Specification<Attention> hasConfigurationServiceId(Long configServiceId) {
        return (root, query, criteriaBuilder) ->
                configServiceId == null ? null : criteriaBuilder.equal(root.get("configurationService").get("id"), configServiceId);
    }
}