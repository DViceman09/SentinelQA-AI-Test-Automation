package com.sentinelqa.framework.ui;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.sentinelqa.framework.Config;

/**
 * One browser per test thread, so TestNG can run UI tests in parallel.
 * BROWSER=chrome|firefox|edge, HEADLESS=true|false, GRID_URL=http://localhost:4444 for Selenium Grid
 * (empty = local browser, driver binaries resolved by Selenium Manager).
 */
public final class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverFactory() {}

    public static WebDriver driver() {
        if (DRIVER.get() == null) DRIVER.set(create());
        return DRIVER.get();
    }

    /** The current thread's browser, without starting one (used by listeners on API tests too). */
    public static Optional<WebDriver> current() {
        return Optional.ofNullable(DRIVER.get());
    }

    public static void quit() {
        current().ifPresent(WebDriver::quit);
        DRIVER.remove();
    }

    private static WebDriver create() {
        String browser = Config.get("browser", "chrome").toLowerCase();
        boolean headless = Config.getBool("headless", true);
        String gridUrl = Config.get("grid.url", "");

        Capabilities options = switch (browser) {
            case "firefox" -> headless ? new FirefoxOptions().addArguments("-headless") : new FirefoxOptions();
            case "edge" -> headless ? new EdgeOptions().addArguments("--headless=new") : new EdgeOptions();
            case "chrome" -> {
                // Chrome's "password found in a data breach" bubble after login swallows the next click.
                ChromeOptions chrome = new ChromeOptions().setExperimentalOption("prefs", Map.of(
                        "credentials_enable_service", false,
                        "profile.password_manager_enabled", false,
                        "profile.password_manager_leak_detection", false));
                yield headless ? chrome.addArguments("--headless=new") : chrome;
            }
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        };

        WebDriver driver = gridUrl.isBlank() ? local(options) : remote(gridUrl, options);
        driver.manage().window().setSize(new Dimension(1366, 900));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
    }

    private static WebDriver local(Capabilities options) {
        return switch (options) {
            case FirefoxOptions o -> new FirefoxDriver(o);
            case EdgeOptions o -> new EdgeDriver(o);
            case ChromeOptions o -> new ChromeDriver(o);
            default -> throw new IllegalStateException();
        };
    }

    private static WebDriver remote(String gridUrl, Capabilities options) {
        try {
            return new RemoteWebDriver(URI.create(gridUrl).toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad GRID_URL: " + gridUrl, e);
        }
    }
}
