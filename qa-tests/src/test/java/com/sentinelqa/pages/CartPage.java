package com.sentinelqa.pages;

import java.math.BigDecimal;
import java.util.List;

import org.openqa.selenium.By;

import com.sentinelqa.framework.ui.BasePage;

public class CartPage extends BasePage {

    public record Line(String name, int quantity, BigDecimal price, BigDecimal subtotal) {}

    private static final By TOTAL = By.id("cart-total");
    private static final By PLACE_ORDER = By.id("place-order");
    private static final By EMPTY = By.id("cart-empty");
    private static final By ROWS = By.cssSelector("[data-testid='cart-row']");

    public BigDecimal total() {
        return new BigDecimal(find(TOTAL, "cart total amount").getText());
    }

    public List<Line> lines() {
        return findAll(ROWS).stream().map(row -> new Line(
                row.findElement(By.className("name")).getText(),
                Integer.parseInt(row.findElement(By.className("qty")).getText()),
                new BigDecimal(row.findElement(By.className("price")).getText()),
                new BigDecimal(row.findElement(By.className("subtotal")).getText()))).toList();
    }

    public String emptyMessage() {
        return find(EMPTY, "message shown when the cart is empty").getText();
    }

    public OrderPage placeOrder() {
        click(PLACE_ORDER, "button that places the order / checks out");
        waitForUrl("/orders/");
        return new OrderPage();
    }

    public CartPage open() {
        open("/cart");
        return this;
    }
}
