package com.sentinelqa.shoplite;

import java.io.IOException;
import java.sql.SQLException;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Docker-free run: {@code ./mvnw -pl shoplite spring-boot:test-run} starts a real (embedded) PostgreSQL
 * on port 5432 with the same database/user as docker compose, then the app on 8080.
 * The database is fresh on every start, so each demo begins from the seed data.
 */
public final class LocalShopLite {

    public static void main(String[] args) throws IOException, SQLException {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPort(5432).start();
        try (var connection = postgres.getPostgresDatabase().getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE shoplite LOGIN PASSWORD 'shoplite'");
            statement.execute("CREATE DATABASE shoplite OWNER shoplite");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // JVM is exiting anyway; the temp data dir is cleaned on the next start
            }
        }));
        SpringApplication.run(ShopLiteApplication.class, args);
    }
}
