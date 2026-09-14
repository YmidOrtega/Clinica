package com.ClinicaDeYmid.auth_service.domain.password;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final PasswordPolicy.Context ANA = new PasswordPolicy.Context("ana.rojas", List.of("ana", "maría", "rojas"));

    private final PasswordPolicy policy = new PasswordPolicy(Set.of("iloveyou2", "correcthorse")::contains, List.of("clinica", "ymid"));

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThat(policy.accept("un caballo verde toma café", ANA).value()).isEqualTo("un caballo verde toma café");
        assertThat(policy.accept("zqxvbnmwpl", ANA).value()).isEqualTo("zqxvbnmwpl");
    }

    @Test
    void normalizesUnicodeBeforeCountingAndComparing() {
        assertThat(policy.accept("ｔｉｇｒｅｓ１２", ANA).value()).isEqualTo("tigres12");
        assertThat(violations("ＩｌｏｖｅＹｏｕ２")).containsExactly(PasswordPolicy.Violation.COMMON);
    }

    @Test
    void enforcesLengthByCharactersNotBytes() {
        assertThat(violations("ñandú7")).containsExactly(PasswordPolicy.Violation.TOO_SHORT);
        assertThat(policy.accept("ñandúñan", ANA).value()).hasSize(8);
        assertThat(violations("correcto caballo bateria grapa ".repeat(5))).containsExactly(PasswordPolicy.Violation.TOO_LONG);
        assertThat(violations(null)).containsExactly(PasswordPolicy.Violation.REQUIRED);
    }

    @Test
    void rejectsCommonPredictableAndPersonalPasswords() {
        assertThat(violations("CorrectHorse")).containsExactly(PasswordPolicy.Violation.COMMON);
        assertThat(violations("aaaaaaaaaa")).containsExactly(PasswordPolicy.Violation.PREDICTABLE);
        assertThat(violations("abababababab")).containsExactly(PasswordPolicy.Violation.PREDICTABLE);
        assertThat(violations("123456789")).containsExactly(PasswordPolicy.Violation.PREDICTABLE);
        assertThat(violations("lkjhgfdsa")).containsExactly(PasswordPolicy.Violation.PREDICTABLE);
        assertThat(violations("ana.rojas-2026")).containsExactly(PasswordPolicy.Violation.PERSONAL_DATA);
        assertThat(violations("siempre ROJAS")).containsExactly(PasswordPolicy.Violation.PERSONAL_DATA);
        assertThat(violations("MiClinica2026")).containsExactly(PasswordPolicy.Violation.SERVICE_WORD);
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThatThrownBy(() -> policy.accept("rojas", ANA))
                .isInstanceOf(PasswordRejectedException.class)
                .hasMessageContaining("al menos 8")
                .hasMessageContaining("nombre del usuario");
    }

    private List<PasswordPolicy.Violation> violations(String password) {
        try {
            policy.accept(password, ANA);
            return List.of();
        } catch (PasswordRejectedException rejected) {
            return rejected.violations();
        }
    }
}
