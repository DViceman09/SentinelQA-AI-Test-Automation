package com.sentinelqa.tests.api;

import static com.sentinelqa.support.Shop.ALICE;
import static com.sentinelqa.support.Shop.BOB;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.api.Api;
import com.sentinelqa.support.Shop;

import io.restassured.path.json.JsonPath;

/**
 * Order rules. Stock-safe test data: Notebook (1000), Wireless Mouse (400) and Sticker Pack (1,000,000) are cheap to
 * order; Limited Edition Poster (5) is only used for orders that must be refused.
 */
@Test(groups = "api")
public class OrderApiTest {

    @Test(description = "Placing an order returns 201 with a correctly calculated total")
    public void orderTotalIsCalculated() {
        BigDecimal price = price("Notebook");

        JsonPath order = Shop.placeOrder(ALICE, "Notebook", 3)
                .then().statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/order.json"))
                .body("status", equalTo("PLACED"))
                .extract().jsonPath();

        assertEquals(order.<BigDecimal>get("total").compareTo(price.multiply(BigDecimal.valueOf(3))), 0,
                "total should be 3 x " + price + " but was " + order.get("total"));
    }

    @Test(description = "An order with several products totals every line correctly")
    public void multiLineOrderTotal() {
        long notebook = Shop.productId("Notebook");
        long mouse = Shop.productId("Wireless Mouse");
        BigDecimal expected = price("Notebook").multiply(BigDecimal.valueOf(2)).add(price("Wireless Mouse"));

        Api.as(ALICE.name(), ALICE.password())
                .body(Map.of("items", List.of(Map.of("productId", notebook, "quantity", 2), Map.of("productId", mouse, "quantity", 1))))
                .post("/api/orders")
                .then().statusCode(201)
                .body("items", hasSize(2))
                .body("total", comparesEqualTo(expected));
    }

    @Test(description = "The same product listed twice is merged into one line with the summed quantity")
    public void duplicateLinesAreMerged() {
        long notebook = Shop.productId("Notebook");

        Api.as(ALICE.name(), ALICE.password())
                .body(Map.of("items", List.of(Map.of("productId", notebook, "quantity", 1), Map.of("productId", notebook, "quantity", 2))))
                .post("/api/orders")
                .then().statusCode(201)
                .body("items", hasSize(1))
                .body("items[0].quantity", equalTo(3));
    }

    @Test(description = "Each line records the catalogue unit price at the time of ordering")
    public void lineKeepsCatalogueUnitPrice() {
        Shop.placeOrder(ALICE, "Wireless Mouse", 1)
                .then().statusCode(201)
                .body("items[0].productName", equalTo("Wireless Mouse"))
                .body("items[0].unitPrice", comparesEqualTo(price("Wireless Mouse")));
    }

    @DataProvider
    public Object[][] validBoundaryQuantities() {
        return new Object[][] {{1}, {100}};
    }

    @Test(dataProvider = "validBoundaryQuantities", description = "Quantities on the allowed boundaries (1 and 100) are accepted")
    public void boundaryQuantitiesAreAccepted(int quantity) {
        Shop.placeOrder(ALICE, "Sticker Pack", quantity).then().statusCode(201).body("items[0].quantity", equalTo(quantity));
    }

    @DataProvider
    public Object[][] invalidQuantities() {
        return new Object[][] {{0}, {-1}, {101}};
    }

    @Test(dataProvider = "invalidQuantities", description = "Quantities outside 1..100 are rejected as validation errors")
    public void invalidQuantityIsRejected(int quantity) {
        Shop.placeOrder(ALICE, "Notebook", quantity).then().statusCode(400);
    }

    @DataProvider
    public Object[][] invalidOrders() {
        return new Object[][] {
                {"empty item list", Map.of("items", List.of()), 400},
                {"items missing", Map.of(), 400},
                {"quantity missing", Map.of("items", List.of(Map.of("productId", 1))), 400},
                {"unknown product", Map.of("items", List.of(Map.of("productId", 999_999, "quantity", 1))), 404},
        };
    }

    @Test(dataProvider = "invalidOrders", description = "Malformed or impossible orders are refused with a precise status code")
    public void invalidOrderIsRefused(String scenario, Map<String, Object> body, int expectedStatus) {
        Api.as(ALICE.name(), ALICE.password()).body(body).post("/api/orders").then().statusCode(expectedStatus);
    }

    @Test(description = "Orders cannot be placed without a bearer token")
    public void orderRequiresAuthentication() {
        Api.given().body(Shop.orderBody(Shop.productId("Notebook"), 1))
                .post("/api/orders")
                .then().statusCode(401);
    }

    @Test(description = "Ordering more than is in stock is refused with 409")
    public void cannotOrderMoreThanStock() {
        Shop.placeOrder(ALICE, "Limited Edition Poster", 50)
                .then().statusCode(409);
    }

    @Test(description = "The customer can read their order back with the same total and lines")
    public void ownerCanReadOrderBack() {
        JsonPath created = Shop.placeOrder(BOB, "Notebook", 2).then().statusCode(201).extract().jsonPath();

        Api.as(BOB.name(), BOB.password()).get("/api/orders/{id}", created.getLong("id"))
                .then().statusCode(200)
                .body("total", comparesEqualTo(created.<BigDecimal>get("total")))
                .body("items[0].productName", equalTo("Notebook"))
                .body("items[0].quantity", equalTo(2));
    }

    @Test(description = "Order history shows the new order, newest first")
    public void orderHistoryListsNewOrderFirst() {
        long id = Shop.placeOrder(BOB, "Notebook", 1).then().statusCode(201).extract().jsonPath().getLong("id");

        List<Long> history = Api.as(BOB.name(), BOB.password()).get("/api/orders")
                .then().statusCode(200).extract().jsonPath().getList("id", Long.class);
        assertTrue(history.contains(id), "new order " + id + " missing from history " + history);
        assertEquals(history, history.stream().sorted(Comparator.reverseOrder()).toList(), "history not newest first");
    }

    @Test(description = "Order history only contains the caller's own orders")
    public void orderHistoryIsPrivate() {
        long aliceOrder = Shop.placeOrder(ALICE, "Notebook", 1).then().statusCode(201).extract().jsonPath().getLong("id");

        JsonPath history = Api.as(BOB.name(), BOB.password()).get("/api/orders")
                .then().statusCode(200).extract().jsonPath();
        // Typed lists on purpose: JSON ids parse as Integer, so a Long-based Hamcrest matcher would pass vacuously.
        assertFalse(history.getList("id", Long.class).contains(aliceOrder), "alice's order is in bob's history");
        List<Long> owners = history.getList("userId", Long.class);
        assertTrue(owners.stream().distinct().count() <= 1, "history mixes customers: " + owners);
    }

    private static BigDecimal price(String product) {
        return Api.given().get("/api/products/{id}", Shop.productId(product)).then().statusCode(200).extract().path("price");
    }
}
