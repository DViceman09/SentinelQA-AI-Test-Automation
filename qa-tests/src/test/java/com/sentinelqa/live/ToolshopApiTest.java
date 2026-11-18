package com.sentinelqa.live;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import io.restassured.path.json.JsonPath;

@Test(groups = "live")
public class ToolshopApiTest {

    // ---- Catalogue ------------------------------------------------------------------------------------------

    @Test(description = "Toolshop API: a product page matches the JSON contract")
    public void productPageMatchesContract() {
        Toolshop.api().get("/products")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/toolshop-products-page.json"));
    }

    @DataProvider
    public Object[][] searchTerms() {
        return new Object[][] {{"pliers"}, {"hammer"}, {"saw"}};
    }

    @Test(dataProvider = "searchTerms", description = "Toolshop API: search only returns matching products")
    public void searchReturnsOnlyMatches(String term) {
        Toolshop.api().queryParam("q", term).get("/products/search")
                .then().statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("data.name", everyItem(containsStringIgnoringCase(term)));
    }

    @DataProvider
    public Object[][] sortOrders() {
        return new Object[][] {{"price,asc"}, {"price,desc"}, {"name,asc"}, {"name,desc"}};
    }

    @Test(dataProvider = "sortOrders", description = "Toolshop API: every sort option returns correctly ordered products")
    public void sortsCorrectly(String sort) {
        JsonPath page = Toolshop.api().queryParam("sort", sort).get("/products").then().statusCode(200).extract().jsonPath();
        boolean descending = sort.endsWith("desc");

        if (sort.startsWith("price")) {
            List<BigDecimal> prices = page.getList("data.price", BigDecimal.class);
            assertFalse(prices.isEmpty());
            assertEquals(prices, sorted(prices, Comparator.<BigDecimal>naturalOrder(), descending), sort);
        } else {
            List<String> names = page.getList("data.name", String.class);
            assertFalse(names.isEmpty());
            assertEquals(names, sorted(names, String.CASE_INSENSITIVE_ORDER, descending), sort);
        }
    }

    @Test(description = "Toolshop API: page 2 is a different slice of the catalogue than page 1")
    public void paginationReturnsDifferentPages() {
        JsonPath first = Toolshop.api().queryParam("page", 1).get("/products").then().statusCode(200).extract().jsonPath();
        JsonPath second = Toolshop.api().queryParam("page", 2).get("/products").then().statusCode(200).extract().jsonPath();

        assertEquals(second.getInt("current_page"), 2);
        List<String> firstIds = first.getList("data.id");
        List<String> secondIds = second.getList("data.id");
        assertFalse(secondIds.isEmpty(), "page 2 is empty");
        assertTrue(Collections.disjoint(firstIds, secondIds), "pages overlap");
        assertTrue(first.getInt("total") > first.getInt("per_page"), "catalogue should need more than one page");
    }

    @Test(description = "Toolshop API: the product detail endpoint agrees with the product list")
    public void detailMatchesList() {
        Map<String, Object> listed = Toolshop.api().get("/products").then().statusCode(200).extract().jsonPath().getMap("data[0]");

        Toolshop.api().get("/products/{id}", listed.get("id"))
                .then().statusCode(200)
                .body("name", equalTo(listed.get("name")))
                .body("price", comparesEqualTo(new BigDecimal(listed.get("price").toString())));
    }

    @Test(description = "Toolshop API: filtering by category only returns products from that category")
    public void categoryFilterOnlyReturnsThatCategory() {
        String hammers = Toolshop.categoryId("Hammer");

        Toolshop.api().queryParam("by_category", hammers).get("/products")
                .then().statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("data.category.id", everyItem(equalTo(hammers)));
    }

    @Test(description = "Toolshop API: the category tree has named, slugged top-level categories with sub-categories")
    public void categoryTreeIsWellFormed() {
        Toolshop.api().get("/categories/tree")
                .then().statusCode(200)
                .body("$", hasSize(greaterThan(0)))
                .body("name", everyItem(not(emptyOrNullString())))
                .body("slug", everyItem(not(emptyOrNullString())))
                .body("findAll { it.sub_categories.size() > 0 }", hasSize(greaterThan(0)));
    }

    @Test(description = "Toolshop API: every brand has an id and a name")
    public void brandsAreWellFormed() {
        Toolshop.api().get("/brands")
                .then().statusCode(200)
                .body("$", hasSize(greaterThan(0)))
                .body("id", everyItem(not(emptyOrNullString())))
                .body("name", everyItem(not(emptyOrNullString())));
    }

    @Test(description = "Toolshop API: unknown product id returns 404")
    public void unknownProductIsNotFound() {
        Toolshop.api().get("/products/{id}", "does-not-exist").then().statusCode(404);
    }

    // ---- Authentication -------------------------------------------------------------------------------------

    @Test(description = "Toolshop API: valid credentials return a short-lived bearer token")
    public void validLoginReturnsBearerToken() {
        Toolshop.api().body(Map.of("email", Toolshop.EMAIL, "password", Toolshop.PASSWORD))
                .post("/users/login")
                .then().statusCode(200)
                .body("access_token", not(emptyOrNullString()))
                .body("token_type", equalTo("bearer"))
                .body("expires_in", greaterThan(0));
    }

    @DataProvider
    public Object[][] invalidLogins() {
        return new Object[][] {
                {"wrong password", Map.of("email", Toolshop.EMAIL, "password", "not-the-password")},
                {"unknown email", Map.of("email", "nobody@example.com", "password", Toolshop.PASSWORD)},
                {"empty password", Map.of("email", Toolshop.EMAIL, "password", "")},
                {"empty body", Map.of()},
        };
    }

    @Test(dataProvider = "invalidLogins", description = "Toolshop API: invalid logins are rejected with 401")
    public void invalidLoginIsRejected(String scenario, Map<String, String> body) {
        Toolshop.api().body(body).post("/users/login").then().statusCode(401);
    }

    @Test(description = "Toolshop API: the profile endpoint needs a token, and with one returns the logged-in user")
    public void profileRequiresAuthentication() {
        Toolshop.api().get("/users/me").then().statusCode(401);
        Toolshop.api().auth().oauth2(Toolshop.token()).get("/users/me")
                .then().statusCode(200)
                .body("email", equalTo(Toolshop.EMAIL));
    }

    @Test(description = "Toolshop API: invoices are private (401 without a token)")
    public void invoicesRequireAuthentication() {
        Toolshop.api().get("/invoices").then().statusCode(401);
    }

    // ---- Cart -----------------------------------------------------------------------------------------------

    @Test(description = "Toolshop API: a cart keeps the quantity and the catalogue price of what was added")
    public void cartKeepsQuantityAndPrice() {
        JsonPath product = firstProduct();
        String cartId = newCartWith(product.getString("data[0].id"), 2);

        Toolshop.api().get("/carts/{id}", cartId)
                .then().statusCode(200)
                .body("cart_items", hasSize(1))
                .body("cart_items[0].quantity", equalTo(2))
                .body("cart_items[0].product.price", comparesEqualTo(product.getObject("data[0].price", BigDecimal.class)));
    }

    @Test(description = "Toolshop API: the quantity of a cart line can be changed")
    public void cartQuantityCanBeUpdated() {
        String productId = firstProduct().getString("data[0].id");
        String cartId = newCartWith(productId, 1);

        Toolshop.api().body(Map.of("product_id", productId, "quantity", 4))
                .put("/carts/{id}/product/quantity", cartId)
                .then().statusCode(200);

        Toolshop.api().get("/carts/{id}", cartId).then().statusCode(200).body("cart_items[0].quantity", equalTo(4));
    }

    @Test(description = "Toolshop API: a product can be removed from the cart")
    public void cartItemCanBeRemoved() {
        String productId = firstProduct().getString("data[0].id");
        String cartId = newCartWith(productId, 1);

        Toolshop.api().delete("/carts/{id}/product/{productId}", cartId, productId).then().statusCode(204);

        Toolshop.api().get("/carts/{id}", cartId).then().statusCode(200).body("cart_items", hasSize(0));
    }

    @Test(description = "Toolshop API: an unknown cart returns 404")
    public void unknownCartIsNotFound() {
        Toolshop.api().get("/carts/{id}", "does-not-exist").then().statusCode(404);
    }

    private static JsonPath firstProduct() {
        return Toolshop.api().get("/products").then().statusCode(200).extract().jsonPath();
    }

    private static String newCartWith(String productId, int quantity) {
        String cartId = Toolshop.api().post("/carts").then().statusCode(201).extract().path("id");
        Toolshop.api().body(Map.of("product_id", productId, "quantity", quantity))
                .post("/carts/{id}", cartId)
                .then().statusCode(200);
        return cartId;
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> order, boolean descending) {
        return values.stream().sorted(descending ? order.reversed() : order).toList();
    }
}
