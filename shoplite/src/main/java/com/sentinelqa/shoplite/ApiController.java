package com.sentinelqa.shoplite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelqa.shoplite.ShopService.Order;
import com.sentinelqa.shoplite.ShopService.Product;
import com.sentinelqa.shoplite.ShopService.User;

import static com.sentinelqa.shoplite.ShopService.error;

@RestController
@RequestMapping("/api")
public class ApiController {

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username) {}
    public record OrderRequest(List<Line> items) {
        public record Line(long productId, int quantity) {}
    }

    // shortcut: tokens live in memory and never expire; restarts log everyone out and it only works
    // on a single instance. Swap for JWT or a Redis session store before running more than one node.
    private final Map<String, User> tokens = new ConcurrentHashMap<>();
    private final ShopService shop;
    private final boolean defects;

    public ApiController(ShopService shop, @Value("${shoplite.defects}") boolean defects) {
        this.shop = shop;
        this.defects = defects;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (isBlank(request.username()) || isBlank(request.password())) {
            throw error(HttpStatus.BAD_REQUEST, "username and password are required");
        }
        User user = shop.login(request.username(), request.password())
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        String token = UUID.randomUUID().toString();
        tokens.put(token, user);
        return new LoginResponse(token, user.username());
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(defaultValue = "") String q) {
        return shop.search(q);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable long id) {
        return shop.product(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Product " + id + " not found"));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order placeOrder(@RequestHeader(value = "Authorization", required = false) String auth,
                            @RequestBody OrderRequest request) {
        User user = authenticate(auth);
        if (request.items() == null) throw error(HttpStatus.BAD_REQUEST, "items are required");
        Map<Long, Integer> items = new LinkedHashMap<>();
        request.items().forEach(line -> items.merge(line.productId(), line.quantity(), Integer::sum));
        return shop.placeOrder(user.id(), items);
    }

    @GetMapping("/orders")
    public List<Order> myOrders(@RequestHeader(value = "Authorization", required = false) String auth) {
        return shop.orders(authenticate(auth).id());
    }

    @GetMapping("/orders/{id}")
    public Order order(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable long id) {
        User user = authenticate(auth);
        return shop.order(id)
                // DEFECT 2 – IDOR: with defects on, any logged-in user can read any order.
                .filter(o -> defects || o.userId() == user.id())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }

    private User authenticate(String auth) {
        User user = auth != null && auth.startsWith("Bearer ") ? tokens.get(auth.substring(7)) : null;
        if (user == null) throw error(HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
        return user;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
