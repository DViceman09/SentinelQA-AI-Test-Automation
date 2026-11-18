package com.sentinelqa.live.pages;

import org.openqa.selenium.By;

import com.sentinelqa.framework.ui.BasePage;
import com.sentinelqa.live.Toolshop;

public class ToolshopLoginPage extends BasePage {

    private static final By EMAIL = By.cssSelector("[data-test='email']");
    private static final By PASSWORD = By.cssSelector("[data-test='password']");
    private static final By SUBMIT = By.cssSelector("[data-test='login-submit']");
    private static final By ERROR = By.cssSelector("[data-test='login-error']");
    private static final By PAGE_TITLE = By.cssSelector("[data-test='page-title']");

    public ToolshopLoginPage open() {
        driver.get(Toolshop.WEB + "/auth/login");
        return this;
    }

    public String loginExpectingError(String email, String password) {
        submit(email, password);
        return find(ERROR, "error message shown after a failed login").getText().trim();
    }

    public String loginAs(String email, String password) {
        submit(email, password);
        waitForUrl("/account");
        return find(PAGE_TITLE, "page heading of the account page").getText().trim();
    }

    private void submit(String email, String password) {
        type(EMAIL, "email input on the login form", email);
        type(PASSWORD, "password input on the login form", password);
        click(SUBMIT, "submit button of the login form");
    }
}
