package com.sentinelqa.tests.ui;

import static com.sentinelqa.support.Shop.BOB;
import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.testng.annotations.Test;

import com.sentinelqa.pages.CartPage;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;

/** Cart behaviour. Nothing is ordered here, so stock is never touched. */
@Test(groups = "ui")
public class CartUiTest extends BaseUiTest {

    @Test(description = "A new session starts with an empty cart")
    public void newSessionHasEmptyCart() {
        ProductsPage products = new LoginPage().open().loginAs(BOB.name(), BOB.password());
        assertEquals(products.cartCount(), 0);

        assertEquals(products.openCart().emptyMessage(), "Your cart is empty.");
    }

    @Test(description = "Adding a product confirms it by name and updates the header counter")
    public void addingProductUpdatesCounter() {
        ProductsPage products = new LoginPage().open().loginAs(BOB.name(), BOB.password()).addToCart("Notebook");

        assertEquals(products.flashMessage(), "Notebook added to cart");
        assertEquals(products.cartCount(), 1);
    }

    @Test(description = "Adding the same product twice gives one line with quantity 2 and a doubled subtotal")
    public void sameProductTwiceMergesIntoOneLine() {
        ProductsPage products = new LoginPage().open().loginAs(BOB.name(), BOB.password());
        BigDecimal price = products.priceOf("Notebook");

        List<CartPage.Line> lines = products.addToCart("Notebook").addToCart("Notebook").openCart().lines();

        assertEquals(lines.size(), 1, "one line expected: " + lines);
        assertEquals(lines.get(0).quantity(), 2);
        assertEquals(lines.get(0).subtotal(), price.multiply(BigDecimal.valueOf(2)));
    }

    @Test(description = "The cart total is the sum of every line's subtotal")
    public void cartTotalIsSumOfLines() {
        ProductsPage products = new LoginPage().open().loginAs(BOB.name(), BOB.password());
        BigDecimal expected = products.priceOf("Notebook").add(products.priceOf("Wireless Mouse"));

        CartPage cart = products.addToCart("Notebook").addToCart("Wireless Mouse").openCart();

        assertEquals(cart.lines().size(), 2);
        assertEquals(cart.total(), expected);
        assertEquals(cart.total(), cart.lines().stream().map(CartPage.Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
