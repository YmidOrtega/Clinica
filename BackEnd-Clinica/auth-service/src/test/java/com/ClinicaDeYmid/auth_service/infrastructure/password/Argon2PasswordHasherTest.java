package com.ClinicaDeYmid.auth_service.infrastructure.password;

import com.ClinicaDeYmid.auth_service.domain.password.NormalizedPassword;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHasherTest {

    private static final Argon2Properties OWASP = new Argon2Properties(16, 32, 1, 19456, 2);

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(OWASP);

    @Test
    void hashesWithArgon2idAndTheOwaspMinimumParameters() {
        PasswordHash hash = hasher.hash(new NormalizedPassword("un caballo verde toma café"));

        assertThat(hash.value()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasher.matches(new NormalizedPassword("un caballo verde toma café"), hash)).isTrue();
        assertThat(hasher.matches(new NormalizedPassword("un caballo verde toma cafe"), hash)).isFalse();
        assertThat(hasher.hash(new NormalizedPassword("un caballo verde toma café"))).isNotEqualTo(hash);
    }

    @Test
    void flagsHashesMadeWithWeakerParametersForRehashing() {
        PasswordHash weaker = new Argon2PasswordHasher(new Argon2Properties(16, 32, 1, 12288, 2)).hash(new NormalizedPassword("frase de paso larga"));

        assertThat(hasher.needsRehash(weaker)).isTrue();
        assertThat(hasher.needsRehash(hasher.hash(new NormalizedPassword("frase de paso larga")))).isFalse();
        assertThat(hasher.matches(new NormalizedPassword("frase de paso larga"), weaker)).isTrue();
    }

    @Test
    void theDecoyCostsAboutAsMuchAsARealVerification() {
        NormalizedPassword password = new NormalizedPassword("frase de paso larga");
        PasswordHash hash = hasher.hash(password);
        hasher.matches(password, hash);
        hasher.matchAgainstDecoy(password);

        long real = elapsedNanos(() -> hasher.matches(password, hash));
        long decoy = elapsedNanos(() -> hasher.matchAgainstDecoy(password));

        assertThat((double) decoy / real).isBetween(0.5, 2.0);
    }

    private static long elapsedNanos(Runnable action) {
        long best = Long.MAX_VALUE;
        for (int attempt = 0; attempt < 5; attempt++) {
            long start = System.nanoTime();
            action.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }
}
