package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Nit {

    private static final Pattern FORMAT = Pattern.compile("^([0-9]{9,10})(?:-([0-9]))?$");
    private static final Pattern SEPARATORS = Pattern.compile("[.\\s]");
    private static final int[] WEIGHTS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    private String number;
    private int verificationDigit;

    protected Nit() {
    }

    public Nit(String value) {
        String cleaned = SEPARATORS.matcher(DomainRules.requiredText(value, "nit", 15)).replaceAll("");
        var match = FORMAT.matcher(cleaned);
        if (!match.matches()) {
            throw new ContractingException.InvalidData("nit", "debe tener entre 9 y 10 dígitos, con o sin dígito de verificación");
        }
        this.number = match.group(1);
        this.verificationDigit = verificationDigitOf(this.number);
        if (match.group(2) != null && Integer.parseInt(match.group(2)) != this.verificationDigit) {
            throw new ContractingException.InvalidData("nit", "tiene un dígito de verificación que no corresponde");
        }
    }

    static int verificationDigitOf(String number) {
        int sum = 0;
        for (int position = 0; position < number.length(); position++) {
            int digit = number.charAt(number.length() - 1 - position) - '0';
            sum += digit * WEIGHTS[position];
        }
        int remainder = sum % 11;
        return remainder < 2 ? remainder : 11 - remainder;
    }

    public String number() {
        return number;
    }

    public int verificationDigit() {
        return verificationDigit;
    }

    public String formatted() {
        return number + "-" + verificationDigit;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Nit that && number.equals(that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return formatted();
    }
}
