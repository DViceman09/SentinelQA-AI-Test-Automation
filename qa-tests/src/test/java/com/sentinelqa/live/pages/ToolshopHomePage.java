package com.sentinelqa.live.pages;

import java.math.BigDecimal;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.sentinelqa.framework.ui.BasePage;
import com.sentinelqa.live.Toolshop;

public class ToolshopHomePage extends BasePage {

    private static final By SEARCH_INPUT = By.cssSelector("[data-test='search-query']");
    private static final By SEARCH_BUTTON = By.cssSelector("[data-test='search-submit']");
    private static final By SEARCH_DONE = By.cssSelector("[data-test='search_completed']");
    private static final By SORT = By.cssSelector("[data-test='sort']");
    private static final By SORT_DONE = By.cssSelector("[data-test='sorting_completed']");
    private static final By NAMES = By.cssSelector("[data-test='product-name']");
    private static final By PRICES = By.cssSelector("[data-test='product-price']");
    private static final By FILTER_DONE = By.cssSelector("[data-test='filter_completed']");
    private static final By LANGUAGE = By.cssSelector("[data-test='language-select']");
    private static final By HOME_LINK = By.cssSelector("[data-test='nav-home']");

    public ToolshopHomePage open() {
        driver.get(Toolshop.WEB + "/");
        find(NAMES, "product name on a product card");
        return this;
    }

    public ToolshopHomePage search(String query) {
        type(SEARCH_INPUT, "product search box", query);
        click(SEARCH_BUTTON, "button that submits the product search");
        find(SEARCH_DONE, "product grid shown once the search has finished");
        return this;
    }

    /** value as the sort dropdown sends it, e.g. "price,asc". */
    public ToolshopHomePage sortBy(String value) {
        new Select(find(SORT, "sort order dropdown")).selectByValue(value);
        find(SORT_DONE, "product grid shown once sorting has finished");
        return this;
    }

    public ToolshopHomePage filterByCategory(String categoryId) {
        click(By.cssSelector("[data-test='category-" + categoryId + "']"), "category filter checkbox " + categoryId);
        find(FILTER_DONE, "product grid shown once filtering has finished");
        return this;
    }

    public ToolshopHomePage switchLanguage(String code) {
        String before = homeLinkText();
        click(LANGUAGE, "language selector in the header");
        click(By.cssSelector("[data-test='lang-" + code + "']"), "language option " + code);
        // Translations load asynchronously: wait until the header actually changes.
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBe(HOME_LINK, before)));
        return this;
    }

    /** Header "Home" link text, e.g. to check the active language. */
    public String homeLinkText() {
        return find(HOME_LINK, "'Home' link in the header").getText().trim();
    }

    public String searchButtonText() {
        return find(SEARCH_BUTTON, "button that submits the product search").getText().trim();
    }

    public List<String> productNames() {
        return findAll(NAMES).stream().map(WebElement::getText).toList();
    }

    public List<BigDecimal> prices() {
        return findAll(PRICES).stream().map(e -> new BigDecimal(e.getText().replace("$", "").trim())).toList();
    }

    public ToolshopProductPage openProduct(String name) {
        click(By.xpath("//*[@data-test='product-name'][normalize-space()='" + name + "']"), "product card for " + name);
        return new ToolshopProductPage();
    }
}
