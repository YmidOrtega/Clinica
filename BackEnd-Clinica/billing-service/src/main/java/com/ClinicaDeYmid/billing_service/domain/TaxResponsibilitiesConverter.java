package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
class TaxResponsibilitiesConverter implements AttributeConverter<Set<TaxResponsibility>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TaxResponsibility> responsibilities) {
        return responsibilities == null ? null : TaxResponsibility.joined(responsibilities);
    }

    @Override
    public Set<TaxResponsibility> convertToEntityAttribute(String codes) {
        return codes == null ? null : Arrays.stream(codes.split(";"))
                .map(TaxResponsibility::ofDianCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
