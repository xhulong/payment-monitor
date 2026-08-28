package org.dromara.system.event;

public record AccountSecurityEvent(
    String type,
    Long userId,
    String email
) {
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
}
