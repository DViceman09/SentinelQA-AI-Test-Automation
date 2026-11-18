package com.sentinelqa.support;

import java.util.List;
import java.util.Map;

import com.sentinelqa.framework.api.Api;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/** ShopLite test data and API shortcuts used to set up tests quickly (no clicking through the UI). */
public final class Shop {

    public record User(String name, String password) {}

    public static final User ALICE = new User("alice", "alice123");
    public static final User BOB = new User("bob", "bob123");

    private Shop() {}

    public static long productId(String name) {
        List<Map<String, Object>> found = Api.given().queryParam("q", name).get("/api/products")
                .then().statusCode(200).extract().jsonPath().getList("findAll { it.name == '" + name + "' }");
        if (found.size() != 1) throw new IllegalStateException("Expected exactly one product named " + name);
        return ((Number) found.get(0).get("id")).longValue();
    }

    public static Map<String, Object> orderBody(long productId, int quantity) {
        return Map.of("items", List.of(Map.of("productId", productId, "quantity", quantity)));
    }

    /** Logs in through the HTML form (not the API) and returns the session cookie, for testing server-rendered pages. */
    public static String webSession(User user) {
        Response login = Api.given().contentType(ContentType.URLENC).redirects().follow(false)
                .formParam("username", user.name()).formParam("password", user.password())
                .post("/login");
        if (login.statusCode() != 302) throw new IllegalStateException("web login failed for " + user.name());
        return login.cookie("JSESSIONID");
    }

    public static Response placeOrder(User user, String productName, int quantity) {
        return Api.as(user.name(), user.password()).body(orderBody(productId(productName), quantity)).post("/api/orders");
    }
}
