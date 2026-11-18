package com.sentinelqa.framework.api;

import java.util.Map;

import com.sentinelqa.framework.Config;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.specification.RequestSpecification;

/** REST Assured entry point: base URI, JSON, exact money values, and every call logged into Allure. */
public final class Api {

    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));

    private Api() {}

    public static RequestSpecification given() {
        return RestAssured.given().spec(new RequestSpecBuilder()
                .setBaseUri(Config.baseUrl())
                .setContentType(ContentType.JSON)
                .setConfig(CONFIG)
                .addFilter(new AllureRestAssured())
                .build());
    }

    /** A request already authenticated as the given user. */
    public static RequestSpecification as(String username, String password) {
        return given().auth().oauth2(token(username, password));
    }

    public static String token(String username, String password) {
        return given().body(Map.of("username", username, "password", password))
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
