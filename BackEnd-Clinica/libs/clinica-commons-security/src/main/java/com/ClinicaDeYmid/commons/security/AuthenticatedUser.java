package com.ClinicaDeYmid.commons.security;

public record AuthenticatedUser(String uuid, Long userId, String email, String role) {
}
