package com.tccs.handler;

import com.tccs.dto.ApiResponse;
import com.tccs.security.SecurityService;
import io.javalin.http.Context;

import java.util.Map;

public class AuthHandler {
    private final SecurityService securityService;

    public AuthHandler(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void login(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String username = body.get("username");
        String password = body.get("password");

        String token = securityService.login(username, password);
        if (token != null) {
            var user = securityService.getUser(token);
            ctx.json(ApiResponse.ok(Map.of(
                    "token", token,
                    "user", user
            )));
        } else {
            ctx.status(401).json(ApiResponse.error("Invalid username or password"));
        }
    }

    public void logout(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            securityService.logout(authHeader.substring(7));
        }
        ctx.json(ApiResponse.ok("Logged out"));
    }

    public void me(Context ctx) {
        var user = ctx.attribute("currentUser");
        if (user != null) {
            ctx.json(ApiResponse.ok(user));
        } else {
            ctx.status(401).json(ApiResponse.error("Not logged in"));
        }
    }
}
