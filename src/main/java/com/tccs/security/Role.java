package com.tccs.security;

import io.javalin.http.Context;
import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    ANYONE,
    BranchOperator,
    TransportManager,
    SystemAdministrator;

    public static Role fromString(String role) {
        if (role == null) return ANYONE;
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return ANYONE;
        }
    }
}
