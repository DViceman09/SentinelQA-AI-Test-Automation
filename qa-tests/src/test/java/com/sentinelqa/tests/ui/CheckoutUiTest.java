package com.sentinelqa.tests.ui;

import static com.sentinelqa.support.Shop.ALICE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.sentinelqa.framework.db.Db;
import com.sentinelqa.pages.CartPage;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.OrderPage;
import com.sentinelqa.pages.ProductsPage;

@Test(groups = "ui")
public class CheckoutUiTest extends BaseUiTest {

    @Test(description = "Search, add to cart and check out; the database stores exactly what the UI showed")
    public void customerCanBuyAProduct() {
        ProductsPage products = new LoginPage().open().loginAs(ALICE.name(), ALICE.password()).search("mug");

        List<String> names = products.productNames();
        assertFalse(names.isEmpty(), "search for 'mug' should find products");
        assertTrue(names.stream().allMatch(n -> n.toLowerCase().contains("mug")), "unexpected search results: " + names);

        BigDecimal price = products.priceOf("Coffee Mug");
        CartPage cart = products.addToCart("Coffee Mug").openCart();
        assertEquals(cart.total(), price, "cart total");

        OrderPage order = cart.placeOrder();
        assertEquals(order.status(), "PLACED");
        assertEquals(order.total(), price, "order total on confirmation page");

        // Cross-layer check: the UI confirmation must match what was persisted.
        Map<String, Object> row = Db.query("""
                SELECT o.total, u.username, count(i.id) AS lines
                FROM orders o JOIN users u ON u.id = o.user_id JOIN order_items i ON i.order_id = o.id
                WHERE o.id = ? GROUP BY o.total, u.username""", order.orderId()).get(0);
        assertEquals(((BigDecimal) row.get("total")).compareTo(price), 0, "total stored in DB: " + row.get("total"));
        assertEquals(row.get("username"), ALICE.name());
        assertEquals(row.get("lines"), 1L);
    }

    @Test(description = "After checkout the new order is listed in the customer's order history")
    public void orderAppearsInHistory() {
        ProductsPage products = new LoginPage().open().loginAs(ALICE.name(), ALICE.password());
        long orderId = products.addToCart("Coffee Mug").openCart().placeOrder().orderId();

        List<Long> history = new ProductsPage().openOrders().orderIds();

        // Only "is listed": parallel tests order as alice too, so position is racy (ordering is covered by OrderApiTest).
        assertTrue(history.contains(orderId), "order " + orderId + " missing from history " + history);
    }

    @Test(description = "Checking out empties the cart")
    public void checkoutEmptiesCart() {
        new LoginPage().open().loginAs(ALICE.name(), ALICE.password()).addToCart("Coffee Mug").openCart().placeOrder();

        ProductsPage products = new ProductsPage();
        assertEquals(products.cartCount(), 0, "header counter after checkout");
        assertEquals(products.openCart().emptyMessage(), "Your cart is empty.");
    }
}
