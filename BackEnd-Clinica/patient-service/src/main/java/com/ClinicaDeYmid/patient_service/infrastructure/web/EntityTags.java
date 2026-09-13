package com.ClinicaDeYmid.patient_service.infrastructure.web;

import com.ClinicaDeYmid.patient_service.application.ApplicationException;
import com.ClinicaDeYmid.patient_service.domain.PatientException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EntityTags {

    private static final Pattern VERSION_TAG = Pattern.compile("^(?:W/)?\"?([0-9]{1,18})\"?$");

    private EntityTags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long versionFrom(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            throw new ApplicationException.VersionRequired();
        }
        Matcher matcher = VERSION_TAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new PatientException.InvalidData("If-Match", "debe contener la versión del paciente, por ejemplo \"3\"");
        }
        return Long.parseLong(matcher.group(1));
    }
}
