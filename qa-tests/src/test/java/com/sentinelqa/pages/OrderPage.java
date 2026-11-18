package com.sentinelqa.pages;

import java.math.BigDecimal;

import org.openqa.selenium.By;

import com.sentinelqa.framework.ui.BasePage;

public class OrderPage extends BasePage {

    private static final By ORDER_ID = By.id("order-id");
    private static final By STATUS = By.id("order-status");
    private static final By TOTAL = By.id("order-total");

    public long orderId() {
        return Long.parseLong(find(ORDER_ID, "order number on the confirmation page").getText());
    }

    public String status() {
        return find(STATUS, "order status").getText();
    }

    public BigDecimal total() {
        return new BigDecimal(find(TOTAL, "order total amount").getText());
    }
}
