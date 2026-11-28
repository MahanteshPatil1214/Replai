package com.replai.tickets.security;

import com.replai.tickets.model.EmployeeRole;

/**
 * Who is calling: built from the JWT and put into the SecurityContext.
 * Controllers receive it with @AuthenticationPrincipal.
 */
public record CurrentUser(Long id, String name, String email, EmployeeRole role) {

    public boolean isLeadOrAdmin() {
        return role == EmployeeRole.LEAD || role == EmployeeRole.ADMIN;
    }
}
