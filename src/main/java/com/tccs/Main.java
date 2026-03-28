package com.tccs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tccs.config.AppConfig;
import com.tccs.dto.ApiResponse;
import com.tccs.handler.*;
import com.tccs.security.Role;
import com.tccs.security.SecurityService;
import com.tccs.service.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

import static io.javalin.apibuilder.ApiBuilder.*;

public class Main {
    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        // 1. Configure Hikari DataSource
        DataSource dataSource = createDataSource(config);

        // 2. Initialize DSLContext
        DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);

        // 3. Configure Jackson for Javalin
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // 4. Initialize Services
        SecurityService securityService = new SecurityService(dsl);
        PricingService pricingService = new PricingService(dsl);
        BillService billService = new BillService(dsl, om, pricingService);
        AllocationService allocationService = new AllocationService(dsl, om, config);
        TruckService truckService = new TruckService(dsl, om);
        ConsignmentService consignmentService = new ConsignmentService(dsl, om, billService, allocationService);
        DispatchService dispatchService = new DispatchService(dsl, om, truckService, consignmentService);

        // 5. Initialize Handlers
        AuthHandler authHandler = new AuthHandler(securityService);
        TruckHandler truckHandler = new TruckHandler(truckService);
        ConsignmentHandler consignmentHandler = new ConsignmentHandler(consignmentService, truckService);
        DispatchHandler dispatchHandler = new DispatchHandler(dispatchService, truckService);
        PricingHandler pricingHandler = new PricingHandler(pricingService);
        DashboardHandler dashboardHandler = new DashboardHandler(dsl, allocationService);
        ReportHandler reportHandler = new ReportHandler(dsl);
        UserHandler userHandler = new UserHandler(dsl);
        AllocationHandler allocationHandler = new AllocationHandler(allocationService);

        // 6. Start Javalin app
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.jsonMapper(new JavalinJackson(om, false));
            javalinConfig.router.apiBuilder(() -> {
                path("api", () -> {
                    path("auth", () -> {
                        post("login", authHandler::login, Role.ANYONE);
                        post("logout", authHandler::logout, Role.ANYONE);
                        get("me", authHandler::me, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                    });
                    path("trucks", () -> {
                        get(truckHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        get("available", truckHandler::getAvailable, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        post(truckHandler::create, Role.TransportManager, Role.SystemAdministrator);
                        path("{id}", () -> {
                            get(truckHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                            patch("status", truckHandler::updateStatus, Role.TransportManager, Role.SystemAdministrator);
                        });
                    });
                    path("allocation", () -> {
                        post("trigger", allocationHandler::trigger, Role.TransportManager, Role.SystemAdministrator);
                        get("pending-volumes", allocationHandler::getPendingVolumes, Role.TransportManager, Role.SystemAdministrator);
                    });
                    path("consignments", () -> {
                        get(consignmentHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        post(consignmentHandler::create, Role.BranchOperator, Role.SystemAdministrator);
                        path("{id}", () -> {
                            get(consignmentHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                            patch("status", consignmentHandler::updateStatus, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        });
                    });
                    path("dispatch", () -> {
                        get(dispatchHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        post(dispatchHandler::create, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        path("{id}", () -> {
                            get(dispatchHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        });
                    });
                    path("pricing", () -> {
                        get(pricingHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                        post(pricingHandler::create, Role.SystemAdministrator);
                    });
                    path("dashboard", () -> {
                        get("stats", dashboardHandler::getStats, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
                    });
                    path("reports", () -> {
                        get("revenue", reportHandler::revenue, Role.TransportManager, Role.SystemAdministrator);
                        get("export/csv", reportHandler::exportCsv, Role.TransportManager, Role.SystemAdministrator);
                    });
                    path("users", () -> {
                        get(userHandler::getAll, Role.SystemAdministrator);
                    });
                    get("health", ctx -> ctx.json(ApiResponse.ok("OK")), Role.ANYONE);
                });
            });

            javalinConfig.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.allowHost(config.get("tccs.cors.allowed-origins", "http://localhost:5173"));
                });
            });
        });

        // Security Filter
        app.beforeMatched(ctx -> {
            var permittedRoles = ctx.routeRoles();
            if (permittedRoles.isEmpty() || permittedRoles.contains(Role.ANYONE)) return;

            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.status(401).json(ApiResponse.error("Unauthorized")).skipRemainingHandlers();
                return;
            }

            var user = securityService.getUser(authHeader.substring(7));
            if (user == null) {
                ctx.status(401).json(ApiResponse.error("Invalid token")).skipRemainingHandlers();
                return;
            }

            if (!permittedRoles.contains(Role.fromString(user.getRole()))) {
                ctx.status(403).json(ApiResponse.error("Forbidden")).skipRemainingHandlers();
                return;
            }

            ctx.attribute("currentUser", user);
        });

        // Exception Handling
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        });

        app.start(config.getInt("server.port", 8080));
    }

    private static DataSource createDataSource(AppConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.get("db.url"));
        hikariConfig.setUsername(config.get("db.username"));
        hikariConfig.setPassword(config.get("db.password"));
        hikariConfig.setMaximumPoolSize(config.getInt("db.pool.max", 10));
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(hikariConfig);
    }
}
