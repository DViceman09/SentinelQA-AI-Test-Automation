package com.sentinelqa.live.pages;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.sentinelqa.framework.ui.BasePage;

/** The 4-step checkout wizard: cart → sign in → billing address → payment. */
public class ToolshopCheckoutPage extends BasePage {

    private static final Pattern INVOICE = Pattern.compile("INV-\\d+");

    private static final By CART_TOTAL = By.cssSelector("[data-test='cart-total']");
    private static final By LINE_QUANTITY = By.cssSelector("[data-test='product-quantity']");
    private static final By LINE_PRICE = By.cssSelector("[data-test='line-price']");
    private static final By PROCEED_CART = By.cssSelector("[data-test='proceed-1']");
    private static final By EMAIL = By.cssSelector("[data-test='email']");
    private static final By PASSWORD = By.cssSelector("[data-test='password']");
    private static final By LOGIN = By.cssSelector("[data-test='login-submit']");
    private static final By PROCEED_SIGNIN = By.cssSelector("[data-test='proceed-2']");
    private static final By COUNTRY = By.cssSelector("[data-test='country']");
    private static final By POSTCODE = By.cssSelector("[data-test='postal_code']");
    private static final By HOUSE_NUMBER = By.cssSelector("[data-test='house_number']");
    private static final By STREET = By.cssSelector("[data-test='street']");
    private static final By CITY = By.cssSelector("[data-test='city']");
    private static final By STATE = By.cssSelector("[data-test='state']");
    private static final By PROCEED_ADDRESS = By.cssSelector("[data-test='proceed-3']");
    private static final By PAYMENT_METHOD = By.cssSelector("[data-test='payment-method']");
    private static final By FINISH = By.cssSelector("[data-test='finish']");
    private static final By PAYMENT_OK = By.cssSelector("[data-test='payment-success-message']");
    private static final By WIZARD = By.tagName("app-checkout");

    public BigDecimal cartTotal() {
        return new BigDecimal(find(CART_TOTAL, "cart total").getText().replace("$", "").trim());
    }

    public int lineQuantity() {
        return Integer.parseInt(find(LINE_QUANTITY, "quantity of the first cart line").getDomProperty("value"));
    }

    public BigDecimal linePrice() {
        return new BigDecimal(find(LINE_PRICE, "price of the first cart line").getText().replace("$", "").trim());
    }

    public ToolshopCheckoutPage signInAndContinue(String email, String password) {
        click(PROCEED_CART, "'Proceed to checkout' button on the cart step");
        type(EMAIL, "email input of the checkout sign-in form", email);
        type(PASSWORD, "password input of the checkout sign-in form", password);
        click(LOGIN, "login button of the checkout sign-in form");
        whenEnabled(PROCEED_SIGNIN).click();
        return this;
    }

    /** Fills every field; the site's postcode lookup may overwrite street/city/state with its own valid values. */
    public ToolshopCheckoutPage billingAddress(String countryCode, String postcode, String houseNumber,
                                               String street, String city, String state) {
        new Select(find(COUNTRY, "billing country dropdown")).selectByValue(countryCode);
        type(STREET, "billing street", street);
        type(CITY, "billing city", city);
        type(STATE, "billing state", state);
        type(POSTCODE, "billing postal code", postcode);
        type(HOUSE_NUMBER, "billing house number", houseNumber);
        whenEnabled(PROCEED_ADDRESS).click();
        return this;
    }

    /** Pays, confirms, and returns the invoice number shown to the customer. */
    public String payWith(String method) {
        new Select(find(PAYMENT_METHOD, "payment method dropdown")).selectByValue(method);
        whenEnabled(FINISH).click();
        find(PAYMENT_OK, "'Payment was successful' message");
        whenEnabled(FINISH).click();
        wait.until(ExpectedConditions.textMatches(WIZARD, INVOICE));
        Matcher invoice = INVOICE.matcher(driver.findElement(WIZARD).getText());
        invoice.find();
        return invoice.group();
    }

    private WebElement whenEnabled(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }
}
