package com.ClinicaDeYmid.billing_service.domain;

import java.util.regex.Pattern;

public record Nit(String number, int verificationDigit) {

    private static final Pattern DIGITS = Pattern.compile("^[1-9][0-9]{5,9}$");
    private static final int[] WEIGHTS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    public Nit {
        number = DomainRules.requiredPattern(number, "nit", DIGITS, "debe tener entre 6 y 10 dígitos, sin puntos ni guion");
        int expected = verificationDigitOf(number);
        if (verificationDigit != expected) {
            throw new BillingException.WrongVerificationDigit(expected);
        }
    }

    public static int verificationDigitOf(String number) {
        int sum = 0;
        for (int position = 0; position < number.length(); position++) {
            int digit = number.charAt(number.length() - 1 - position) - '0';
            sum += digit * WEIGHTS[position];
        }
        int remainder = sum % 11;
        return remainder > 1 ? 11 - remainder : remainder;
    }

    public String formatted() {
        return number + "-" + verificationDigit;
    }
}
