package com.ClinicaDeYmid.auth_service.domain.password;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private static final int MIN_PERSONAL_WORD_LENGTH = 4;
    private static final String SEQUENCES = "abcdefghijklmnopqrstuvwxyz 0123456789 qwertyuiop asdfghjkl zxcvbnm";

    public enum Violation {
        REQUIRED("es obligatoria"),
        TOO_SHORT("debe tener al menos " + MIN_LENGTH + " caracteres"),
        TOO_LONG("no puede superar " + MAX_LENGTH + " caracteres"),
        COMMON("es una contraseña común o filtrada"),
        PREDICTABLE("es una repetición o una secuencia"),
        PERSONAL_DATA("contiene el correo o el nombre del usuario"),
        SERVICE_WORD("contiene el nombre de la institución u otra palabra prohibida");

        private final String message;

        Violation(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public record Context(String emailLocalPart, Collection<String> nameWords) {

        public Context {
            nameWords = List.copyOf(nameWords);
        }
    }

    private final PasswordDenyList denyList;
    private final Set<String> serviceWords;

    public PasswordPolicy(PasswordDenyList denyList, Collection<String> serviceWords) {
        this.denyList = denyList;
        this.serviceWords = serviceWords.stream()
                .map(word -> Normalizer.normalize(word.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public NormalizedPassword accept(String rawPassword, Context context) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new PasswordRejectedException(List.of(Violation.REQUIRED));
        }
        String normalized = Normalizer.normalize(rawPassword, Normalizer.Form.NFKC);
        String lowercase = normalized.toLowerCase(Locale.ROOT);
        int length = normalized.codePointCount(0, normalized.length());
        List<Violation> violations = new ArrayList<>();
        if (length < MIN_LENGTH) {
            violations.add(Violation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            violations.add(Violation.TOO_LONG);
        }
        if (denyList.contains(lowercase)) {
            violations.add(Violation.COMMON);
        }
        if (repeated(lowercase) || sequential(lowercase)) {
            violations.add(Violation.PREDICTABLE);
        }
        if (containsPersonalData(lowercase, context)) {
            violations.add(Violation.PERSONAL_DATA);
        }
        if (serviceWords.stream().anyMatch(lowercase::contains)) {
            violations.add(Violation.SERVICE_WORD);
        }
        if (!violations.isEmpty()) {
            throw new PasswordRejectedException(violations);
        }
        return new NormalizedPassword(normalized);
    }

    public NormalizedPassword normalize(String rawPassword) {
        return new NormalizedPassword(Normalizer.normalize(rawPassword == null ? "" : rawPassword, Normalizer.Form.NFKC));
    }

    private static boolean repeated(String password) {
        return password.codePoints().distinct().count() <= 2;
    }

    private static boolean sequential(String password) {
        String reversed = new StringBuilder(password).reverse().toString();
        return SEQUENCES.contains(password) || SEQUENCES.contains(reversed);
    }

    private static boolean containsPersonalData(String password, Context context) {
        if (context == null) {
            return false;
        }
        String local = context.emailLocalPart() == null ? "" : context.emailLocalPart().toLowerCase(Locale.ROOT);
        if (local.length() >= MIN_PERSONAL_WORD_LENGTH && password.contains(local)) {
            return true;
        }
        return context.nameWords().stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .filter(word -> word.length() >= MIN_PERSONAL_WORD_LENGTH)
                .anyMatch(password::contains);
    }
}
