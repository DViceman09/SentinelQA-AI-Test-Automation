package com.sentinelqa.tests.db;

import static com.sentinelqa.support.Shop.ALICE;
import static com.sentinelqa.support.Shop.BOB;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.api.Api;
import com.sentinelqa.framework.db.Db;
import com.sentinelqa.support.Shop;

import io.restassured.path.json.JsonPath;

/**
 * Database-level checks. Reserved test data so parallel tests can't interfere:
 * 'Desk Plant' (stock decrement), 'Travel Mug' (rollback), 'Limited Edition Poster' (oversell race).
 */
@Test(groups = "db")
public class DataIntegrityTest {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @Test(description = "Every Flyway migration applied successfully")
    public void migrationsApplied() {
        assertEquals(Db.scalar("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Long.class), 0L);
        assertTrue(Db.scalar("SELECT count(*) FROM flyway_schema_history", Long.class) >= 2, "expected schema + seed migrations");
    }

    @Test(description = "All seed products exist exactly once (reference data intact)")
    public void seedCatalogueIsComplete() {
        List<String> expected = List.of("Coffee Mug", "Travel Mug", "Notebook", "Mechanical Keyboard", "Wireless Mouse",
                "Desk Plant", "Limited Edition Poster", "Sticker Pack");
        for (String name : expected) {
            assertEquals(Db.scalar("SELECT count(*) FROM products WHERE name = ?", Long.class, name), 1L, name);
        }
    }

    @DataProvider
    public Object[][] requiredIndexes() {
        return new Object[][] {{"idx_orders_user"}, {"idx_order_items_order"}};
    }

    @Test(dataProvider = "requiredIndexes", description = "Indexes the order history depends on exist (performance guard)")
    public void indexExists(String index) {
        assertEquals(Db.scalar("SELECT count(*) FROM pg_indexes WHERE indexname = ?", Long.class, index), 1L);
    }

    @Test(description = "Placing an order through the API decrements stock in the database")
    public void orderDecrementsStock() {
        int before = stock("Desk Plant");

        Shop.placeOrder(BOB, "Desk Plant", 2).then().statusCode(201);

        assertEquals(stock("Desk Plant"), before - 2, "stock must drop by the ordered quantity (before=" + before + ")");
    }

    @Test(description = "What the API returned for an order is exactly what was stored")
    public void apiOrderMatchesDatabase() {
        JsonPath order = Shop.placeOrder(BOB, "Notebook", 2).then().statusCode(201).extract().jsonPath();
        long id = order.getLong("id");

        Map<String, Object> row = Db.query("""
                SELECT o.total, o.status, u.username, i.quantity, i.unit_price, p.name
                FROM orders o JOIN users u ON u.id = o.user_id
                JOIN order_items i ON i.order_id = o.id JOIN products p ON p.id = i.product_id
                WHERE o.id = ?""", id).get(0);
        assertEquals(((BigDecimal) row.get("total")).compareTo(order.get("total")), 0, "total");
        assertEquals(row.get("status"), "PLACED");
        assertEquals(row.get("username"), BOB.name());
        assertEquals(row.get("quantity"), 2);
        assertEquals(row.get("name"), "Notebook");
    }

    @Test(description = "A failed order is rolled back completely: no stock is taken from the lines that did fit")
    public void failedOrderIsRolledBack() {
        int before = stock("Travel Mug");
        long travelMug = Shop.productId("Travel Mug");
        long poster = Shop.productId("Limited Edition Poster");

        // Travel Mug is processed first and fits; the poster line then fails on stock.
        Api.as(ALICE.name(), ALICE.password())
                .body(Map.of("items", List.of(Map.of("productId", travelMug, "quantity", 1), Map.of("productId", poster, "quantity", 50))))
                .post("/api/orders")
                .then().statusCode(409);

        assertEquals(stock("Travel Mug"), before, "transaction must roll back the Travel Mug decrement");
    }

    @Test(description = "Two customers racing for the last items can never oversell (row-level stock check)")
    public void concurrentOrdersCannotOversell() throws Exception {
        Db.update("UPDATE products SET stock = 3 WHERE name = 'Limited Edition Poster'");
        try {
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Integer> alice = CompletableFuture.supplyAsync(() -> buyPosters(start, ALICE));
            CompletableFuture<Integer> bob = CompletableFuture.supplyAsync(() -> buyPosters(start, BOB));
            start.countDown();
            List<Integer> statuses = List.of(alice.get(), bob.get()).stream().sorted().toList();

            assertEquals(statuses, List.of(201, 409), "exactly one of two orders for 2 of the last 3 posters may succeed");
            assertEquals(stock("Limited Edition Poster"), 1, "stock after one successful order of 2");
        } finally {
            Db.update("UPDATE products SET stock = 5 WHERE name = 'Limited Edition Poster'");
        }
    }

    @Test(description = "Every stored order total equals the sum of its line items")
    public void orderTotalsMatchLineItems() {
        List<Map<String, Object>> mismatches = Db.query("""
                SELECT o.id, o.total, SUM(i.quantity * i.unit_price) AS line_sum
                FROM orders o JOIN order_items i ON i.order_id = o.id
                GROUP BY o.id, o.total
                HAVING o.total <> SUM(i.quantity * i.unit_price)""");
        assertTrue(mismatches.isEmpty(), "orders with wrong totals: " + mismatches);
    }

    @Test(description = "Every order has at least one line (no empty orders)")
    public void everyOrderHasLines() {
        assertEquals(Db.scalar("""
                SELECT count(*) FROM orders o
                WHERE NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id)""", Long.class), 0L);
    }

    // Prices are static in ShopLite, so every line must carry today's catalogue price.
    @Test(description = "Order lines store the catalogue unit price")
    public void lineUnitPricesMatchCatalogue() {
        assertEquals(Db.scalar("""
                SELECT count(*) FROM order_items i JOIN products p ON p.id = i.product_id
                WHERE i.unit_price <> p.price""", Long.class), 0L);
    }

    @Test(description = "Deleting an order removes its lines too (ON DELETE CASCADE)")
    public void deletingOrderCascadesToLines() {
        long id = Shop.placeOrder(BOB, "Notebook", 1).then().statusCode(201).extract().jsonPath().getLong("id");

        Db.update("DELETE FROM orders WHERE id = ?", id);

        assertEquals(Db.scalar("SELECT count(*) FROM order_items WHERE order_id = ?", Long.class, id), 0L);
    }

    @Test(description = "The database itself refuses negative stock (CHECK constraint)")
    public void negativeStockIsRejectedByDatabase() {
        assertSqlState(CHECK_VIOLATION, "UPDATE products SET stock = -1 WHERE name = 'Desk Plant'");
    }

    @Test(description = "The database refuses a zero or negative price (CHECK constraint)")
    public void nonPositivePriceIsRejectedByDatabase() {
        assertSqlState(CHECK_VIOLATION, "UPDATE products SET price = 0 WHERE name = 'Desk Plant'");
    }

    @Test(description = "The database refuses an order line with quantity 0 (CHECK constraint)")
    public void zeroQuantityLineIsRejectedByDatabase() {
        assertSqlState(CHECK_VIOLATION, "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (1, 1, 0, 1)");
    }

    @Test(description = "The database refuses a line pointing at a missing order or product (foreign keys)")
    public void orphanLineIsRejectedByDatabase() {
        assertSqlState(FOREIGN_KEY_VIOLATION,
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (999999, 999999, 1, 1)");
    }

    @Test(description = "Usernames are unique (UNIQUE constraint)")
    public void duplicateUsernameIsRejectedByDatabase() {
        assertSqlState(UNIQUE_VIOLATION, "INSERT INTO users (username, password_hash) VALUES ('alice', 'x')");
    }

    @Test(description = "Passwords are stored as BCrypt hashes, never as plain text")
    public void passwordsAreHashed() {
        assertEquals(Db.scalar("SELECT count(*) FROM users WHERE password_hash !~ '^\\$2[aby]\\$'", Long.class), 0L);
    }

    private static int stock(String product) {
        return Db.scalar("SELECT stock FROM products WHERE name = ?", Integer.class, product);
    }

    private static void assertSqlState(String expected, String sql) {
        Db.DbException error = expectThrows(Db.DbException.class, () -> Db.update(sql));
        assertEquals(error.sqlState(), expected, "unexpected SQLState: " + error.getMessage());
    }

    private static int buyPosters(CountDownLatch start, Shop.User user) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Shop.placeOrder(user, "Limited Edition Poster", 2).statusCode();
    }
}
