package com.sentinelqa.shoplite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** All business logic and SQL. When shoplite.defects=true it plants the defects listed in the README. */
@Service
public class ShopService {

    public record User(long id, String username) {}
    public record Product(long id, String name, String description, BigDecimal price, int stock) {}
    public record OrderLine(long productId, String productName, int quantity, BigDecimal unitPrice) {}
    public record Order(long id, long userId, BigDecimal total, String status, OffsetDateTime createdAt, List<OrderLine> items) {}
    private record Credentials(long id, String passwordHash) {}

    private static final PasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    private static final String SELECT_PRODUCTS = "SELECT id, name, description, price, stock FROM products";

    private final JdbcClient db;
    private final boolean defects;

    public ShopService(JdbcClient db, @Value("${shoplite.defects}") boolean defects) {
        this.db = db;
        this.defects = defects;
    }

    public Optional<User> login(String username, String password) {
        return db.sql("SELECT id, password_hash FROM users WHERE username = ?").param(username)
                .query(Credentials.class).optional()
                .filter(c -> PASSWORDS.matches(password, c.passwordHash()))
                .map(c -> new User(c.id(), username));
    }

    public List<Product> search(String query) {
        String term = query == null ? "" : query.trim();
        if (defects) {
            // DEFECT 1 – SQL injection: user input concatenated straight into SQL.
            return db.sql(SELECT_PRODUCTS + " WHERE lower(name) LIKE lower('%" + term + "%') ORDER BY id")
                    .query(Product.class).list();
        }
        return db.sql(SELECT_PRODUCTS + " WHERE lower(name) LIKE lower(?) ORDER BY id")
                .param("%" + term + "%").query(Product.class).list();
    }

    public Optional<Product> product(long id) {
        return db.sql(SELECT_PRODUCTS + " WHERE id = ?").param(id).query(Product.class).optional();
    }

    @Transactional
    public Order placeOrder(long userId, Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        BigDecimal total = BigDecimal.ZERO;
        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> item : items.entrySet()) {
            int quantity = item.getValue() == null ? 0 : item.getValue();
            if (quantity < 1 || quantity > 100) throw error(HttpStatus.BAD_REQUEST, "Quantity must be between 1 and 100");
            Product p = product(item.getKey())
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Product " + item.getKey() + " not found"));
            if (p.stock() < quantity) throw error(HttpStatus.CONFLICT, "Not enough stock for " + p.name());
            // DEFECT 4 – when defects are on, stock is never decremented.
            if (!defects) {
                int updated = db.sql("UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?")
                        .params(quantity, p.id(), quantity).update();
                // Re-checked in SQL so two concurrent orders cannot both take the last item.
                if (updated == 0) throw error(HttpStatus.CONFLICT, "Not enough stock for " + p.name());
            }
            lines.add(new OrderLine(p.id(), p.name(), quantity, p.price()));
            total = total.add(p.price().multiply(BigDecimal.valueOf(quantity)));
        }
        long orderId = db.sql("INSERT INTO orders (user_id, total) VALUES (?, ?) RETURNING id")
                .params(userId, total).query(Long.class).single();
        for (OrderLine line : lines) {
            db.sql("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)")
                    .params(orderId, line.productId(), line.quantity(), line.unitPrice()).update();
        }
        return order(orderId).orElseThrow();
    }

    public Optional<Order> order(long id) {
        List<OrderLine> lines = db.sql("""
                SELECT i.product_id, p.name AS product_name, i.quantity, i.unit_price
                FROM order_items i JOIN products p ON p.id = i.product_id
                WHERE i.order_id = ? ORDER BY i.id""").param(id).query(OrderLine.class).list();
        return db.sql("SELECT id, user_id, total, status, created_at FROM orders WHERE id = ?").param(id)
                .query((rs, n) -> new Order(rs.getLong("id"), rs.getLong("user_id"), rs.getBigDecimal("total"),
                        rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class), lines))
                .optional();
    }

    // shortcut: one query per order (N+1); fine for demo data, batch-load items if order history grows.
    public List<Order> orders(long userId) {
        return db.sql("SELECT id FROM orders WHERE user_id = ? ORDER BY id DESC").param(userId)
                .query(Long.class).list().stream().map(id -> order(id).orElseThrow()).toList();
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
