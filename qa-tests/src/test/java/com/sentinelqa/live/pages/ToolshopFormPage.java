package com.sentinelqa.live.pages;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.sentinelqa.framework.ui.BasePage;
import com.sentinelqa.live.Toolshop;

/**
 * Any Toolshop form (login, contact, register): every field and its error message follow the same
 * data-test convention ("email" / "email-error"), so one page object covers client-side validation for all of them.
 */
public class ToolshopFormPage extends BasePage {

    private static final By ERRORS = By.cssSelector("[data-test$='-error']");

    public ToolshopFormPage openAt(String path) {
        driver.get(Toolshop.WEB + path);
        return this;
    }

    public ToolshopFormPage fill(String field, String value) {
        type(By.cssSelector("[data-test='" + field + "']"), "form field " + field, value);
        return this;
    }

    public ToolshopFormPage submit(String submitButton) {
        click(By.cssSelector("[data-test='" + submitButton + "']"), "submit button " + submitButton);
        find(ERRORS, "validation message under a form field");
        // Angular renders the messages one by one: wait until the count is the same on two polls in a row.
        int[] last = {-1};
        wait.until(d -> {
            int now = errors().size();
            boolean stable = now == last[0];
            last[0] = now;
            return stable;
        });
        return this;
    }

    /** Visible validation messages by field, e.g. {"email": "Email is required"}. */
    public Map<String, String> errors() {
        Map<String, String> errors = new LinkedHashMap<>();
        for (WebElement e : findAll(ERRORS)) {
            if (e.isDisplayed()) errors.put(e.getDomAttribute("data-test").replaceFirst("-error$", ""), e.getText().trim());
        }
        return errors;
    }
}
