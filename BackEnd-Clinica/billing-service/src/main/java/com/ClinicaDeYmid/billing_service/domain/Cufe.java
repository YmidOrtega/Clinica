package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class Cufe {

    public static final String VAT = "01";
    public static final String CONSUMPTION_TAX = "04";
    public static final String INDUSTRY_TAX = "03";
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final String BOGOTA_OFFSET = "-05:00";

    private Cufe() {
    }

    public record Input(String number, LocalDate issueDate, LocalTime issueTime, BigDecimal lineExtensionAmount,
                        BigDecimal vat, BigDecimal consumptionTax, BigDecimal industryTax, BigDecimal payableAmount,
                        String issuerNit, String buyerDocument, String technicalKey, DianEnvironment environment) {
    }

    public static String of(Input input) {
        return sha384(concatenation(input));
    }

    public static String concatenation(Input input) {
        return input.number()
                + input.issueDate()
                + time(input.issueTime())
                + amount(input.lineExtensionAmount())
                + VAT + amount(input.vat())
                + CONSUMPTION_TAX + amount(input.consumptionTax())
                + INDUSTRY_TAX + amount(input.industryTax())
                + amount(input.payableAmount())
                + input.issuerNit()
                + input.buyerDocument()
                + input.technicalKey()
                + input.environment().dianCode();
    }

    public static String softwareSecurityCode(String softwareId, String pin, String number) {
        return sha384(softwareId + pin + number);
    }

    public static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String time(LocalTime time) {
        return TIME.format(time) + BOGOTA_OFFSET;
    }

    public static String withoutVerificationDigit(String document) {
        int dash = document.indexOf('-');
        return dash < 0 ? document : document.substring(0, dash);
    }

    static String sha384(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
