package com.sentinelqa.shoplite;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sentinelqa.shoplite.ShopService.Product;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static com.sentinelqa.shoplite.ShopService.error;

/** Server-rendered pages driven by the Selenium tests. Element ids are the tests' stable locators. */
@Controller
public class UiController {

    public record CartLine(Product product, int quantity, BigDecimal subtotal) {}

    private final ShopService shop;
    private final boolean redesign;

    public UiController(ShopService shop, @Value("${shoplite.redesign}") boolean redesign) {
        this.shop = shop;
        this.redesign = redesign;
    }

    @ModelAttribute("redesign")
    public boolean redesign() {
        return redesign;
    }

    @ModelAttribute("username")
    public String username(HttpSession session) {
        return (String) session.getAttribute("username");
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpSession session) {
        return cart(session).values().stream().mapToInt(Integer::intValue).sum();
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpServletRequest request, Model model) {
        var user = shop.login(username, password);
        if (user.isEmpty()) {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
        request.changeSessionId(); // prevents session fixation
        HttpSession session = request.getSession();
        session.setAttribute("userId", user.get().id());
        session.setAttribute("username", user.get().username());
        return "redirect:/products";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "") String q, HttpSession session, Model model) {
        if (userId(session) == null) return "redirect:/login";
        model.addAttribute("q", q);
        model.addAttribute("products", shop.search(q));
        return "products";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam long productId, HttpSession session, RedirectAttributes redirect) {
        if (userId(session) == null) return "redirect:/login";
        Product product = shop.product(productId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Product " + productId + " not found"));
        cart(session).merge(productId, 1, Integer::sum);
        redirect.addFlashAttribute("message", product.name() + " added to cart");
        return "redirect:/products";
    }

    @GetMapping("/cart")
    public String cartPage(HttpSession session, Model model) {
        if (userId(session) == null) return "redirect:/login";
        List<CartLine> lines = cart(session).entrySet().stream()
                .flatMap(e -> shop.product(e.getKey()).stream()
                        .map(p -> new CartLine(p, e.getValue(), p.price().multiply(BigDecimal.valueOf(e.getValue())))))
                .toList();
        model.addAttribute("lines", lines);
        model.addAttribute("total", lines.stream().map(CartLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "cart";
    }

    @PostMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        Long userId = userId(session);
        if (userId == null) return "redirect:/login";
        try {
            var order = shop.placeOrder(userId, cart(session));
            cart(session).clear();
            return "redirect:/orders/" + order.id();
        } catch (ResponseStatusException e) {
            model.addAttribute("error", e.getReason());
            return cartPage(session, model);
        }
    }

    @GetMapping("/orders")
    public String orders(HttpSession session, Model model) {
        Long userId = userId(session);
        if (userId == null) return "redirect:/login";
        model.addAttribute("orders", shop.orders(userId));
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable long id, HttpSession session, Model model) {
        Long userId = userId(session);
        if (userId == null) return "redirect:/login";
        model.addAttribute("order", shop.order(id).filter(o -> o.userId() == userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order " + id + " not found")));
        return "order";
    }

    private static Long userId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer> cart(HttpSession session) {
        var cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute("cart", cart);
        }
        return cart;
    }
}
