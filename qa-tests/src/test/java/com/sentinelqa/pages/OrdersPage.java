package com.sentinelqa.pages;

import java.util.List;

import org.openqa.selenium.By;

import com.sentinelqa.framework.ui.BasePage;

/** Order history ("Your orders"). */
public class OrdersPage extends BasePage {

    private static final By TABLE = By.id("orders");
    private static final By ORDER_LINKS = By.cssSelector("[data-testid='order-row'] a");

    public List<Long> orderIds() {
        find(TABLE, "order history table");
        return findAll(ORDER_LINKS).stream().map(a -> Long.parseLong(a.getText().replace("#", "").trim())).toList();
    }
}
