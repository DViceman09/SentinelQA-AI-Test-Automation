package com.sentinelqa.pages;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.sentinelqa.framework.ui.BasePage;

public class ProductsPage extends BasePage {

    private static final By SEARCH_INPUT = By.id("search-input");
    private static final By SEARCH_BUTTON = By.id("search-button");
    private static final By TABLE = By.id("products");
    private static final By ROWS = By.cssSelector("[data-testid='product-row']");
    private static final By FLASH = By.id("flash");
    private static final By CART_LINK = By.id("nav-cart");
    private static final By CURRENT_USER = By.id("current-user");
    private static final By CART_COUNT = By.id("cart-count");
    private static final By NO_RESULTS = By.id("no-results");
    private static final By ORDERS_LINK = By.id("nav-orders");
    private static final By LOGOUT = By.id("logout");

    public ProductsPage search(String query) {
        type(SEARCH_INPUT, "product search box", query);
        click(SEARCH_BUTTON, "button that submits the product search");
        waitForUrl("q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        return this;
    }

    public List<String> productNames() {
        find(TABLE, "products table");
        return findAll(ROWS).stream().map(row -> row.findElement(By.className("name")).getText()).toList();
    }

    public BigDecimal priceOf(String productName) {
        return new BigDecimal(row(productName).findElement(By.className("price")).getText());
    }

    public ProductsPage addToCart(String productName) {
        String expectedCount = String.valueOf(cartCount() + 1);
        click(By.xpath(rowXpath(productName) + "//button[contains(@class,'add-to-cart')]"),
                "'Add to cart' button in the row for " + productName);
        // The counter is re-rendered by the redirect, so this also proves the new page has loaded.
        wait.until(ExpectedConditions.textToBe(CART_COUNT, expectedCount));
        find(FLASH, "confirmation message after adding to cart");
        return this;
    }

    public int cartCount() {
        return Integer.parseInt(find(CART_COUNT, "number of items in the cart, in the header").getText().trim());
    }

    public String flashMessage() {
        return find(FLASH, "confirmation message after adding to cart").getText();
    }

    public String noResultsMessage() {
        return find(NO_RESULTS, "message shown when a search finds nothing").getText();
    }

    public String currentUser() {
        return find(CURRENT_USER, "logged-in username in the header").getText();
    }

    public CartPage openCart() {
        click(CART_LINK, "cart link in the header");
        waitForUrl("/cart");
        return new CartPage();
    }

    public OrdersPage openOrders() {
        click(ORDERS_LINK, "orders link in the header");
        waitForUrl("/orders");
        return new OrdersPage();
    }

    public LoginPage logout() {
        click(LOGOUT, "log out button in the header");
        waitForUrl("/login");
        return new LoginPage();
    }

    private WebElement row(String productName) {
        return find(By.xpath(rowXpath(productName)), "table row for product " + productName);
    }

    private static String rowXpath(String productName) {
        return "//tr[@data-testid='product-row'][td[@class='name' and normalize-space()='" + productName + "']]";
    }
}
