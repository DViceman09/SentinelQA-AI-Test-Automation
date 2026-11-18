package com.sentinelqa.live.pages;

import java.math.BigDecimal;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.sentinelqa.framework.ui.BasePage;
import com.sentinelqa.live.Toolshop;

public class ToolshopProductPage extends BasePage {

    private static final By NAME = By.cssSelector("[data-test='product-name']");
    private static final By PRICE = By.cssSelector("[data-test='unit-price']");
    private static final By QUANTITY = By.cssSelector("[data-test='quantity']");
    private static final By INCREASE = By.cssSelector("[data-test='increase-quantity']");
    private static final By ADD_TO_CART = By.cssSelector("[data-test='add-to-cart']");
    private static final By OUT_OF_STOCK = By.cssSelector("[data-test='out-of-stock']");
    private static final By CART_COUNT = By.cssSelector("[data-test='cart-quantity']");
    private static final By CART_LINK = By.cssSelector("[data-test='nav-cart']");

    /** Opens a product directly by its id (from the API). */
    public ToolshopProductPage openById(String productId) {
        driver.get(Toolshop.WEB + "/product/" + productId);
        find(NAME, "product title");
        return this;
    }

    public String name() {
        return find(NAME, "product title").getText().trim();
    }

    public BigDecimal price() {
        return new BigDecimal(find(PRICE, "unit price of the product").getText().trim());
    }

    public ToolshopProductPage increaseQuantity(int times) {
        for (int i = 0; i < times; i++) click(INCREASE, "'+' button next to the quantity");
        return this;
    }

    public int quantity() {
        return Integer.parseInt(find(QUANTITY, "quantity input").getDomProperty("value"));
    }

    /** Adds to cart and waits until the header counter shows the expected number of items. */
    public ToolshopProductPage addToCart(int expectedCartCount) {
        click(ADD_TO_CART, "'Add to cart' button");
        wait.until(ExpectedConditions.textToBe(CART_COUNT, String.valueOf(expectedCartCount)));
        return this;
    }

    public ToolshopProductPage addToCart() {
        return addToCart(1);
    }

    public String cartCount() {
        return find(CART_COUNT, "cart item counter in the header").getText().trim();
    }

    public boolean canAddToCart() {
        return find(ADD_TO_CART, "'Add to cart' button").isEnabled();
    }

    public boolean showsOutOfStock() {
        return find(OUT_OF_STOCK, "'Out of stock' label").isDisplayed();
    }

    public ToolshopCheckoutPage openCart() {
        click(CART_LINK, "cart icon in the header");
        return new ToolshopCheckoutPage();
    }
}
