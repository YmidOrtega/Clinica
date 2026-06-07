package com.ClinicaDeYmid.billing_service.infra.security;

public class UserContextHolder {

    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void setCurrentUserId(String userId) {
        userIdHolder.set(userId);
    }

    public static String getCurrentUserId() {
        String userId = userIdHolder.get();
        return userId != null ? userId : "SYSTEM";
    }

    public static void clear() {
        userIdHolder.remove();
    }
}
