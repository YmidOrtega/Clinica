package com.ClinicaDeYmid.auth_service.domain.password;

public interface PasswordDenyList {

    boolean contains(String lowercasePassword);
}
