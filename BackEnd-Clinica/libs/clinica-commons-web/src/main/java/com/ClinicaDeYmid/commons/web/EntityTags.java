package com.ClinicaDeYmid.commons.web;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntityTags {

    private static final Pattern VERSION_TAG = Pattern.compile("^(?:W/)?\"?([0-9]{1,18})\"?$");

    private EntityTags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    public static long requiredVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            throw new VersionRequired();
        }
        Matcher matcher = VERSION_TAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new MalformedVersion();
        }
        return Long.parseLong(matcher.group(1));
    }

    public static final class VersionRequired extends DomainException {
        VersionRequired() {
            super(ErrorCategory.PRECONDITION_REQUIRED, "VERSION_REQUIRED",
                    "Envía la cabecera If-Match con la versión del recurso que vas a modificar");
        }
    }

    public static final class MalformedVersion extends DomainException {
        MalformedVersion() {
            super(ErrorCategory.INVALID_INPUT, "MALFORMED_VERSION", "La cabecera If-Match debe contener una versión, por ejemplo \"3\"");
        }
    }

    public static final class StaleVersion extends DomainException {
        public StaleVersion() {
            super(ErrorCategory.PRECONDITION_FAILED, "VERSION_MISMATCH",
                    "El recurso fue modificado después de consultarlo; vuelve a consultarlo antes de editar");
        }
    }
}
