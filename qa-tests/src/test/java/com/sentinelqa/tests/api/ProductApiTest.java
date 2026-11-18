package com.sentinelqa.tests.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.api.Api;
import com.sentinelqa.framework.db.Db;

@Test(groups = "api")
public class ProductApiTest {

    @Test(description = "Product list matches the published JSON contract")
    public void productListMatchesContract() {
        Api.given().get("/api/products")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/products.json"));
    }

    @DataProvider
    public Object[][] searchTerms() {
        return new Object[][] {{"mug"}, {"MUG"}, {"Mug"}, {"  mug  "}, {"key"}, {"note"}};
    }

    @Test(dataProvider = "searchTerms", description = "Search is case-insensitive, trims spaces and only returns matching products")
    public void searchReturnsOnlyMatches(String term) {
        Api.given().queryParam("q", term).get("/api/products")
                .then().statusCode(200)
                .body("$", hasSize(greaterThan(0)))
                .body("name", everyItem(containsStringIgnoringCase(term.trim())));
    }

    @Test(description = "A search with no match returns an empty list, not an error")
    public void searchWithoutMatchIsEmpty() {
        Api.given().queryParam("q", "no-such-product-xyz").get("/api/products")
                .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test(description = "Without a search term the API returns the whole catalogue (API matches the database)")
    public void emptySearchReturnsWholeCatalogue() {
        long inDb = Db.scalar("SELECT count(*) FROM products", Long.class);

        Api.given().get("/api/products").then().statusCode(200).body("$", hasSize((int) inDb));
    }

    @Test(description = "Products are listed in a stable order (by id)")
    public void catalogueIsOrderedById() {
        List<Long> ids = Api.given().get("/api/products").then().extract().jsonPath().getList("id", Long.class);

        assertEquals(ids, ids.stream().sorted().toList());
    }

    // 'Mechanical Keyboard' is never ordered by any test, so its stock can't change between the two calls.
    @Test(description = "The product detail endpoint returns exactly what the catalogue lists")
    public void detailMatchesCatalogueEntry() {
        Map<String, Object> listed = Api.given().get("/api/products").then().extract().jsonPath()
                .getMap("find { it.name == 'Mechanical Keyboard' }");

        Api.given().get("/api/products/{id}", listed.get("id"))
                .then().statusCode(200)
                .body("name", equalTo(listed.get("name")))
                .body("description", equalTo(listed.get("description")))
                .body("price", equalTo(listed.get("price")))
                .body("stock", equalTo(listed.get("stock")));
    }

    @DataProvider
    public Object[][] unknownIds() {
        return new Object[][] {{999_999}, {0}, {-1}};
    }

    @Test(dataProvider = "unknownIds", description = "Unknown product ids return 404")
    public void unknownProductIsNotFound(long id) {
        Api.given().get("/api/products/{id}", id).then().statusCode(404);
    }

    @Test(description = "A non-numeric product id is a 400 client error, not a 500")
    public void nonNumericIdIsBadRequest() {
        Api.given().get("/api/products/{id}", "abc").then().statusCode(400);
    }
}
