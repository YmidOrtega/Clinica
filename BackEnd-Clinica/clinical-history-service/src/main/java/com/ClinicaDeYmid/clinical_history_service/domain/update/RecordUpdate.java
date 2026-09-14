package com.ClinicaDeYmid.clinical_history_service.domain.update;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public sealed interface RecordUpdate {

    int MAX_PER_NOTE = 50;

    record AddListItem(ListItemDetails details) implements RecordUpdate {

        public AddListItem {
            ClinicalText.present(details, "details");
        }
    }

    record ChangeListItemStatus(UUID itemId, ListItemStatus status, String reason) implements RecordUpdate {

        public ChangeListItemStatus {
            ClinicalText.present(itemId, "itemId");
            ClinicalText.present(status, "status");
            reason = ClinicalText.required(reason, "reason", ClinicalText.SHORT);
        }
    }

    record RecordVitalSigns(Instant measuredAt, List<Reading> readings) implements RecordUpdate {

        public record Reading(VitalSignKind kind, BigDecimal value) {

            public Reading {
                ClinicalText.present(kind, "readings.kind");
                value = kind.validate(value);
            }
        }

        public RecordVitalSigns {
            measuredAt = ClinicalText.present(measuredAt, "measuredAt").truncatedTo(ChronoUnit.MICROS);
            readings = readings == null ? List.of() : List.copyOf(readings);
            if (readings.isEmpty()) {
                throw new ClinicalException.InvalidData("readings", "debe tener al menos una medición");
            }
            Map<VitalSignKind, BigDecimal> byKind;
            try {
                byKind = readings.stream().collect(Collectors.toMap(Reading::kind, Reading::value));
            } catch (IllegalStateException duplicated) {
                throw new ClinicalException.InvalidData("readings", "repite un tipo de signo vital en la misma toma");
            }
            BigDecimal systolic = byKind.get(VitalSignKind.SYSTOLIC_BLOOD_PRESSURE);
            BigDecimal diastolic = byKind.get(VitalSignKind.DIASTOLIC_BLOOD_PRESSURE);
            if (systolic != null && diastolic != null && diastolic.compareTo(systolic) >= 0) {
                throw new ClinicalException.InvalidData("readings", "la presión diastólica debe ser menor que la sistólica");
            }
        }
    }

    static List<RecordUpdate> normalize(List<RecordUpdate> updates) {
        List<RecordUpdate> list = updates == null ? List.of() : List.copyOf(updates);
        if (list.size() > MAX_PER_NOTE) {
            throw new ClinicalException.InvalidData("updates", "no puede tener más de " + MAX_PER_NOTE + " actualizaciones");
        }
        Map<UUID, Long> changesPerItem = list.stream()
                .filter(ChangeListItemStatus.class::isInstance)
                .map(ChangeListItemStatus.class::cast)
                .collect(Collectors.groupingBy(ChangeListItemStatus::itemId, Collectors.counting()));
        if (changesPerItem.values().stream().anyMatch(count -> count > 1)) {
            throw new ClinicalException.InvalidData("updates", "cambia el mismo ítem más de una vez");
        }
        return list;
    }

    static Map<UUID, ChangeListItemStatus> changesByItem(List<RecordUpdate> updates) {
        return updates.stream()
                .filter(ChangeListItemStatus.class::isInstance)
                .map(ChangeListItemStatus.class::cast)
                .collect(Collectors.toMap(ChangeListItemStatus::itemId, Function.identity()));
    }
}
