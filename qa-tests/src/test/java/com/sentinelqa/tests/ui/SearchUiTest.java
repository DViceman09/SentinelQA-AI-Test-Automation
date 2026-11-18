package com.sentinelqa.tests.ui;

import static com.sentinelqa.support.Shop.BOB;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.db.Db;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;

@Test(groups = "ui")
public class SearchUiTest extends BaseUiTest {

    @DataProvider
    public Object[][] searchTerms() {
        return new Object[][] {{"mug"}, {"note"}, {"KEY"}};
    }

    @Test(dataProvider = "searchTerms", description = "Search shows only products whose name matches, ignoring case")
    public void searchShowsOnlyMatches(String term) {
        List<String> names = loggedIn().search(term).productNames();

        assertFalse(names.isEmpty(), "search for '" + term + "' should find products");
        assertTrue(names.stream().allMatch(n -> n.toLowerCase().contains(term.toLowerCase())), "unexpected results: " + names);
    }

    @Test(description = "A search without matches says so instead of showing an empty table")
    public void searchWithoutMatchesShowsMessage() {
        ProductsPage products = loggedIn().search("no-such-product-xyz");

        assertEquals(products.noResultsMessage(), "No products found.");
        assertTrue(products.productNames().isEmpty());
    }

    @Test(description = "The catalogue page lists every product in the database (UI matches DB)")
    public void catalogueShowsEveryProduct() {
        List<String> inDb = Db.query("SELECT name FROM products ORDER BY id").stream().map(r -> (String) r.get("name")).toList();

        assertEquals(loggedIn().productNames(), inDb);
    }

    private static ProductsPage loggedIn() {
        return new LoginPage().open().loginAs(BOB.name(), BOB.password());
    }
}
