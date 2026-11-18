package com.sentinelqa.live;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.live.pages.ToolshopCheckoutPage;
import com.sentinelqa.live.pages.ToolshopFormPage;
import com.sentinelqa.live.pages.ToolshopHomePage;
import com.sentinelqa.live.pages.ToolshopLoginPage;
import com.sentinelqa.live.pages.ToolshopProductPage;
import com.sentinelqa.tests.ui.BaseUiTest;

/** Same framework as the ShopLite UI tests (page objects, self-healing, screenshots, AI triage), on a live third-party site. */
@Test(groups = "live")
public class ToolshopUiTest extends BaseUiTest {

    // ---- Catalogue ------------------------------------------------------------------------------------------

    @DataProvider
    public Object[][] searchTerms() {
        return new Object[][] {{"pliers"}, {"hammer"}, {"saw"}};
    }

    @Test(dataProvider = "searchTerms", description = "Toolshop: search only shows products whose name matches")
    public void searchShowsOnlyMatchingProducts(String term) {
        List<String> names = new ToolshopHomePage().open().search(term).productNames();

        assertFalse(names.isEmpty(), "search for '" + term + "' should find products");
        assertTrue(names.stream().allMatch(n -> n.toLowerCase().contains(term)), "unexpected search results: " + names);
    }

    @DataProvider
    public Object[][] sortOrders() {
        return new Object[][] {{"price,asc"}, {"price,desc"}, {"name,asc"}, {"name,desc"}};
    }

    @Test(dataProvider = "sortOrders", description = "Toolshop: every sort option really orders the product grid")
    public void sortingOrdersTheGrid(String sort) {
        ToolshopHomePage home = new ToolshopHomePage().open().sortBy(sort);
        boolean descending = sort.endsWith("desc");

        if (sort.startsWith("price")) {
            List<BigDecimal> prices = home.prices();
            assertFalse(prices.isEmpty(), "product grid is empty");
            assertEquals(prices, sorted(prices, Comparator.<BigDecimal>naturalOrder(), descending), sort);
        } else {
            List<String> names = home.productNames();
            assertFalse(names.isEmpty(), "product grid is empty");
            assertEquals(names, sorted(names, String.CASE_INSENSITIVE_ORDER, descending), sort);
        }
    }

    @Test(description = "Toolshop: the category filter shows exactly the products the API lists for that category")
    public void categoryFilterMatchesApi() {
        String hammers = Toolshop.categoryId("Hammer");
        List<String> api = Toolshop.api().queryParam("by_category", hammers).get("/products")
                .then().statusCode(200).extract().jsonPath().getList("data.name");

        List<String> ui = new ToolshopHomePage().open().filterByCategory(hammers).productNames();

        assertEquals(ui, api, "UI and API disagree on the Hammer category");
    }

    @Test(description = "Toolshop: the product page shows the same price as the API")
    public void productPagePriceMatchesApi() {
        Map<String, Object> product = Toolshop.api().get("/products").then().statusCode(200).extract().jsonPath().getMap("data[0]");

        ToolshopProductPage page = new ToolshopProductPage().openById((String) product.get("id"));

        assertEquals(page.name(), product.get("name"));
        assertEquals(page.price().compareTo(new BigDecimal(product.get("price").toString())), 0, "price");
    }

    @Test(description = "Toolshop: an out-of-stock product is labelled and cannot be added to the cart")
    public void outOfStockProductCannotBeAdded() {
        List<String> outOfStock = Toolshop.api().get("/products").then().statusCode(200)
                .extract().jsonPath().getList("data.findAll { !it.in_stock }.id");
        if (outOfStock.isEmpty()) throw new SkipException("no out-of-stock product on the first page today");

        ToolshopProductPage page = new ToolshopProductPage().openById(outOfStock.get(0));

        assertTrue(page.showsOutOfStock(), "'Out of stock' label missing");
        assertFalse(page.canAddToCart(), "'Add to cart' must be disabled");
    }

    @Test(description = "Toolshop: switching the language to German translates the navigation")
    public void languageSwitchTranslatesUi() {
        ToolshopHomePage home = new ToolshopHomePage().open().switchLanguage("de");

        assertEquals(home.homeLinkText(), "Start");
        assertEquals(home.searchButtonText(), "Suche");
    }

    // ---- Cart and checkout ----------------------------------------------------------------------------------

    @Test(description = "Toolshop: choosing quantity 3 puts 3 items in the cart at 3 x the unit price")
    public void quantitySelectorDrivesCartTotal() {
        ToolshopProductPage product = new ToolshopHomePage().open().search("pliers").openProduct("Combination Pliers");
        BigDecimal price = product.price();

        product.increaseQuantity(2);
        assertEquals(product.quantity(), 3);
        ToolshopCheckoutPage cart = product.addToCart(3).openCart();

        assertEquals(cart.lineQuantity(), 3);
        assertEquals(cart.linePrice(), price.multiply(BigDecimal.valueOf(3)));
        assertEquals(cart.cartTotal(), price.multiply(BigDecimal.valueOf(3)));
    }

    @Test(description = "Toolshop: search, add to cart and check out; the invoice shown in the UI exists in the backend with the same total")
    public void customerCanBuyAProduct() {
        ToolshopProductPage product = new ToolshopHomePage().open().search("pliers").openProduct("Combination Pliers");
        BigDecimal price = product.price();

        ToolshopCheckoutPage checkout = product.addToCart().openCart();
        assertEquals(checkout.cartTotal(), price, "cart total");

        String invoice = checkout
                .signInAndContinue(Toolshop.EMAIL, Toolshop.PASSWORD)
                .billingAddress("AT", "1010", "98", "Test street", "Vienna", "Vienna")
                .payWith("cash-on-delivery");

        // Cross-layer check: what the customer was shown must match what the backend stored.
        Toolshop.api().auth().oauth2(Toolshop.token())
                .queryParam("q", invoice)
                .get("/invoices/search")
                .then().statusCode(200)
                .body("data[0].invoice_number", equalTo(invoice))
                .body("data[0].total", comparesEqualTo(price))
                .body("data[0].invoicelines[0].product.name", equalTo("Combination Pliers"));
    }

    // ---- Accounts and forms ---------------------------------------------------------------------------------

    @Test(description = "Toolshop: the demo customer can sign in and reaches 'My account'")
    public void customerCanSignIn() {
        assertEquals(new ToolshopLoginPage().open().loginAs(Toolshop.EMAIL, Toolshop.PASSWORD), "My account");
    }

    @Test(description = "Toolshop: a wrong password is refused with a clear message")
    public void wrongPasswordIsRefused() {
        String error = new ToolshopLoginPage().open().loginExpectingError(Toolshop.EMAIL, "not-the-password");

        assertEquals(error, "Invalid email or password");
    }

    @DataProvider
    public Object[][] loginValidation() {
        return new Object[][] {
                {"", Map.of("email", "Email is required", "password", "Password is required")},
                {"not-an-email", Map.of("email", "Email format is invalid", "password", "Password is required")},
        };
    }

    @Test(dataProvider = "loginValidation", description = "Toolshop: the login form validates email and password before sending anything")
    public void loginFormValidatesInput(String email, Map<String, String> expected) {
        ToolshopFormPage form = new ToolshopFormPage().openAt("/auth/login");
        if (!email.isEmpty()) form.fill("email", email);

        assertEquals(form.submit("login-submit").errors(), expected);
    }

    @Test(description = "Toolshop: the contact form requires every field")
    public void contactFormRequiresAllFields() {
        Map<String, String> errors = new ToolshopFormPage().openAt("/contact").submit("contact-submit").errors();

        assertEquals(errors, Map.of(
                "first-name", "First name is required",
                "last-name", "Last name is required",
                "email", "Email is required",
                "subject", "Subject is required",
                "message", "Message is required"));
    }

    @Test(description = "Toolshop: the registration form flags every required field")
    public void registrationFormRequiresAllFields() {
        Map<String, String> errors = new ToolshopFormPage().openAt("/auth/register").submit("register-submit").errors();

        assertTrue(errors.keySet().containsAll(List.of("first-name", "last-name", "dob", "country", "postal_code",
                "house_number", "street", "city", "state", "phone", "email", "password")), "missing errors: " + errors.keySet());
        assertEquals(errors.get("email"), "Email is required");
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> order, boolean descending) {
        return values.stream().sorted(descending ? order.reversed() : order).toList();
    }
}
