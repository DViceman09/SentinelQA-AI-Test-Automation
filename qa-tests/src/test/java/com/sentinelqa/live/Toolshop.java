package com.sentinelqa.live;

import java.util.Map;

import com.sentinelqa.framework.Config;
import com.sentinelqa.framework.api.Api;

import io.restassured.specification.RequestSpecification;

/**
 * practicesoftwaretesting.com ("Toolshop"): a live, public e-commerce site built for test automation practice.
 * Its data is shared with everyone practising on it, so tests assert rules (results match, list is sorted,
 * totals add up) and never exact catalogue contents. Functional tests only: no load or security scans on a site we don't own.
 */
public final class Toolshop {

    public static final String WEB = Config.get("toolshop.url", "https://practicesoftwaretesting.com");
    public static final String API = Config.get("toolshop.api.url", "https://api.practicesoftwaretesting.com");

    // Public demo account, published on the site itself.
    public static final String EMAIL = "customer@practicesoftwaretesting.com";
    public static final String PASSWORD = "welcome01";
    // Second public demo account, used only for wrong-password tests: Toolshop locks an account (423) after failed
    // logins, and the happy-path tests must keep working on EMAIL.
    public static final String WRONG_PASSWORD_EMAIL = "customer2@practicesoftwaretesting.com";

    private Toolshop() {}

    /** Same REST Assured setup as the ShopLite tests (JSON, BigDecimal numbers, Allure logging), pointed at Toolshop. */
    public static RequestSpecification api() {
        return Api.given().baseUri(API);
    }

    /** Ids change when the site's data is reset, so tests look categories up by name. */
    public static String categoryId(String name) {
        String id = api().get("/categories/tree").then().statusCode(200).extract().jsonPath()
                .getString("collect { it.sub_categories }.flatten().find { it.name == '" + name + "' }.id");
        if (id == null) throw new IllegalStateException("No Toolshop category named " + name);
        return id;
    }

    public static String token() {
        return api().body(Map.of("email", EMAIL, "password", PASSWORD))
                .post("/users/login")
                .then().statusCode(200)
                .extract().path("access_token");
    }
}
