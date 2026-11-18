package com.sentinelqa.tests.ui;

import static com.sentinelqa.support.Shop.ALICE;
import static com.sentinelqa.support.Shop.BOB;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.pages.CartPage;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;

@Test(groups = "ui")
public class LoginUiTest extends BaseUiTest {

    @DataProvider
    public Object[][] customers() {
        return new Object[][] {{ALICE.name(), ALICE.password()}, {BOB.name(), BOB.password()}};
    }

    @Test(dataProvider = "customers", description = "A customer logs in and lands on the product catalogue under their own name")
    public void customerCanLogIn(String username, String password) {
        ProductsPage products = new LoginPage().open().loginAs(username, password);

        assertEquals(products.currentUser(), username);
        assertFalse(products.productNames().isEmpty(), "catalogue should list products");
    }

    @DataProvider
    public Object[][] invalidLogins() {
        return new Object[][] {
                {"wrong password", ALICE.name(), "wrong-password"},
                {"unknown user", "mallory", "mallory123"},
                {"username is case-sensitive", "ALICE", ALICE.password()},
        };
    }

    @Test(dataProvider = "invalidLogins", description = "Invalid credentials show one generic error and do not log the user in")
    public void invalidLoginIsRejected(String scenario, String username, String password) {
        LoginPage login = new LoginPage().open().loginExpectingError(username, password);

        assertEquals(login.error(), "Invalid username or password");
        assertTrue(login.isDisplayed(), "user should stay on the login page");
    }

    @Test(description = "Logging out ends the session: shop pages send the user back to login")
    public void logoutEndsSession() {
        LoginPage login = new LoginPage().open().loginAs(ALICE.name(), ALICE.password()).logout();
        assertTrue(login.isDisplayed(), "logout should land on the login page");

        new CartPage().open();
        assertTrue(new LoginPage().isDisplayed(), "cart must not be reachable after logout");
    }

    @Test(description = "An anonymous visitor opening the cart is redirected to login")
    public void anonymousVisitorIsRedirectedToLogin() {
        new CartPage().open();

        assertTrue(new LoginPage().isDisplayed());
    }
}
