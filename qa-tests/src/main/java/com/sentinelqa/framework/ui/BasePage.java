package com.sentinelqa.framework.ui;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.sentinelqa.framework.Config;
import com.sentinelqa.framework.ai.SelfHealer;

/**
 * Base for page objects. Every lookup carries a plain-English "intent" so that when a locator
 * breaks (e.g. after a UI redesign) the AI healer knows what element the test meant.
 */
public abstract class BasePage {

    protected final WebDriver driver = DriverFactory.driver();
    protected final WebDriverWait wait =
            new WebDriverWait(driver, Duration.ofSeconds(Config.getInt("ui.timeout.seconds", 8)));

    protected WebElement find(By locator, String intent) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(SelfHealer.healed(locator)));
        } catch (TimeoutException notFound) {
            return SelfHealer.heal(driver, locator, intent).orElseThrow(() -> notFound);
        }
    }

    protected List<WebElement> findAll(By locator) {
        return driver.findElements(locator);
    }

    protected void click(By locator, String intent) {
        find(locator, intent).click();
    }

    protected void type(By locator, String intent, String text) {
        WebElement element = find(locator, intent);
        element.clear();
        element.sendKeys(text);
    }

    protected void open(String path) {
        driver.get(Config.uiBaseUrl() + path);
    }

    protected void waitForUrl(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }
}
