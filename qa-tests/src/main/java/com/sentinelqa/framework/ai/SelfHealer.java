package com.sentinelqa.framework.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.qameta.allure.Allure;

/**
 * AI self-healing locators. When a locator stops matching, the LLM reads the current page and
 * proposes a CSS selector for the element's described purpose. The heal is used only if it matches
 * exactly one element, and every heal is reported so a human updates the page object afterwards:
 * healing keeps the run green but never hides the drift.
 */
public final class SelfHealer {

    interface Healer {
        @SystemMessage("""
                You repair broken Selenium locators. Given what an element is for, the locator that no longer
                matches, and the current page HTML, return ONE CSS selector that uniquely matches the intended
                element. Prefer id, name, data-testid or aria attributes over position or styling classes.""")
        @UserMessage("""
                Element purpose: {{intent}}
                Broken locator: {{broken}}
                Page HTML:
                {{html}}""")
        Suggestion suggest(@V("intent") String intent, @V("broken") String broken, @V("html") String html);
    }

    public record Suggestion(String cssSelector, String reason) {}

    static final Path REPORT = Path.of("target", "self-healing-report.md");
    private static final Map<String, By> HEALED = new ConcurrentHashMap<>();

    private SelfHealer() {}

    /** The locator to use now: the healed one if this locator was healed earlier in the run. */
    public static By healed(By original) {
        return HEALED.getOrDefault(original.toString(), original);
    }

    public static Optional<WebElement> heal(WebDriver driver, By broken, String intent) {
        if (!Ai.enabled()) return Optional.empty();
        try {
            Suggestion suggestion = Ai.service(Healer.class).suggest(intent, broken.toString(), compact(driver.getPageSource()));
            By candidate = By.cssSelector(suggestion.cssSelector());
            List<WebElement> matches = driver.findElements(candidate);
            if (matches.size() != 1) {
                System.err.printf("[self-heal] rejected '%s' for %s: matched %d elements%n", suggestion.cssSelector(), broken, matches.size());
                return Optional.empty(); // never guess between several elements
            }
            if (HEALED.putIfAbsent(broken.toString(), candidate) == null) { // parallel tests may heal the same locator; report it once
                MarkdownReport.appendRow(REPORT, "| Intent | Broken locator | Healed locator | Why |",
                        intent, broken.toString(), candidate.toString(), String.valueOf(suggestion.reason()));
            }
            Allure.attachment("AI self-healed locator", "Intent: %s%nBroken: %s%nHealed: %s%nWhy: %s"
                    .formatted(intent, broken, candidate, suggestion.reason()));
            System.out.printf("[self-heal] %s -> %s (%s)%n", broken, candidate, intent);
            return Optional.of(matches.get(0));
        } catch (RuntimeException e) {
            System.err.println("[self-heal] AI call failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Drops scripts, styles and comments so the page fits in the model's context. */
    static String compact(String html) {
        String text = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("\\s+", " ");
        // shortcut: hard cut at 20k chars; send only the region around the old element if pages grow larger.
        return text.length() > 20_000 ? text.substring(0, 20_000) : text;
    }
}
