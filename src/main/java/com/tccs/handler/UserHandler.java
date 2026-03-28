package com.tccs.handler;

import com.tccs.dto.ApiResponse;
import io.javalin.http.Context;
import org.jooq.DSLContext;

import java.util.Map;

import static com.tccs.db.Tables.USERS;

public class UserHandler {
    private final DSLContext dsl;

    public UserHandler(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.ok(Map.of("users", dsl.selectFrom(USERS).fetch())));
    }
}
