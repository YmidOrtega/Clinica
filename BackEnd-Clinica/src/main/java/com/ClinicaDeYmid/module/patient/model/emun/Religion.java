package com.ClinicaDeYmid.module.patient.model.emun;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Religion {
    CATHOLIC("Católica"),
    PROTESTANT("Protestante"),
    JEWISH("Judía"),
    MUSLIM("Musulmana"),
    BUDDHIST("Budista"),
    HINDU("Hindu"),
    ATHEIST("Ateo"),
    AGNOSTIC("Agnóstico"),
    OTHER("Otra");

    private final String description;
}
