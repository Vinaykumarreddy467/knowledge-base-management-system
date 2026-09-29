package com.kbms.security;

import com.kbms.common.ApiException;
import com.kbms.user.AppUser;
import com.kbms.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Single place the current principal is read, so services never repeat the cast. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AppUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUser user)) {
            throw new ApiException(401, "UNAUTHENTICATED", "Authentication is required");
        }
        return user;
    }

    public static boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AppUser user
                && user.getRole().isStaff();
    }

    public static void requireStaff() {
        if (!isStaff()) {
            throw ApiException.forbidden("Editor or administrator role is required");
        }
    }

    public static void requireAdmin() {
        if (require().getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Administrator role is required");
        }
    }
}
