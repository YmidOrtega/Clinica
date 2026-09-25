package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.StayChargeCommands;
import com.ClinicaDeYmid.billing_service.application.StayQueries;
import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayPeriods;
import com.ClinicaDeYmid.billing_service.domain.StaySegment;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Estancia", description = "Días cama por periodos de 24 h según el tipo de estancia de cada habitación")
class StayController {

    static final String STAY_CHARGES = "/api/v1/billing/stay-charges";

    private final StayQueries queries;
    private final StayChargeCommands commands;

    StayController(StayQueries queries, StayChargeCommands commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping(AccountController.BASE_PATH + "/{admissionNumber}/stay")
    @PreAuthorize(Access.READ)
    @Operation(summary = "Tramos de cama y periodos de 24 h de un episodio",
            description = "Con el episodio abierto cuenta hasta ahora; al egreso esta es la base de la venta de estancia")
    StayView stay(@PathVariable @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber) {
        return StayView.from(queries.of(admissionNumber));
    }

    @GetMapping(STAY_CHARGES)
    @PreAuthorize(Access.READ)
    @Operation(summary = "Con qué servicio del portafolio se cobra cada tipo de estancia")
    List<StayChargeView> charges() {
        return queries.charges().stream().map(StayChargeView::from).toList();
    }

    @PutMapping(STAY_CHARGES + "/{stayType}")
    @PreAuthorize(Access.MANAGE_CONFIG)
    @Operation(summary = "Definir el servicio del portafolio con que se cobra un tipo de estancia")
    StayChargeView bill(@PathVariable StayType stayType, @Valid @RequestBody Billing request) {
        return StayChargeView.from(commands.bill(stayType, request.portfolioItemUuid()));
    }

    record Billing(@NotNull UUID portfolioItemUuid) {
    }

    record StayChargeView(StayType stayType, UUID portfolioItemUuid, String cupsCode, String description) {

        static StayChargeView from(StayCharge charge) {
            return new StayChargeView(charge.stayType(), charge.service().portfolioItemUuid(),
                    charge.service().cupsCode(), charge.service().description());
        }
    }

    record SegmentView(UUID bedUuid, StayType stayType, Instant startedAt, Instant endedAt) {

        static SegmentView from(StaySegment segment) {
            return new SegmentView(segment.bedUuid(), segment.stayType(), segment.startedAt(), segment.endedAt());
        }
    }

    record RunView(StayType stayType, LocalDate startsOn, Instant from, Instant to, int periods, String cupsCode,
                   boolean billable) {
    }

    record StayView(String admissionNumber, Instant countedUntil, List<SegmentView> segments, List<RunView> periods,
                    int totalPeriods, List<StayType> unbillableTypes) {

        static StayView from(StayQueries.Stay stay) {
            List<RunView> runs = stay.runs().stream().map(run -> {
                StayCharge charge = run.stayType() == null ? null : stay.charges().get(run.stayType());
                return new RunView(run.stayType(), run.startsOn(), run.from(), run.to(), run.periods(),
                        charge == null ? null : charge.service().cupsCode(), charge != null);
            }).toList();
            return new StayView(stay.account().admissionNumber(), stay.until(),
                    stay.segments().stream().map(SegmentView::from).toList(), runs,
                    stay.runs().stream().mapToInt(StayPeriods.Run::periods).sum(),
                    runs.stream().filter(run -> !run.billable()).map(RunView::stayType).distinct().toList());
        }
    }
}
