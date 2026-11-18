package com.sentinelqa.pages;

import org.openqa.selenium.By;

import com.sentinelqa.framework.ui.BasePage;

public class LoginPage extends BasePage {

    private static final By USERNAME = By.id("username");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT = By.id("login-button");
    private static final By ERROR = By.id("login-error");

    public LoginPage open() {
        open("/login");
        return this;
    }

    public ProductsPage loginAs(String username, String password) {
        submit(username, password);
        waitForUrl("/products");
        return new ProductsPage();
    }

    public LoginPage loginExpectingError(String username, String password) {
        submit(username, password);
        return this;
    }

    /** True when the login form is on screen, i.e. the user is (back) on the login page. */
    public boolean isDisplayed() {
        waitForUrl("/login");
        return find(USERNAME, "username input on the login form").isDisplayed();
    }

    public String error() {
        return find(ERROR, "error message shown after a failed login").getText();
    }

    private void submit(String username, String password) {
        type(USERNAME, "username input on the login form", username);
        type(PASSWORD, "password input on the login form", password);
        click(SUBMIT, "submit button of the login form");
    }
}
