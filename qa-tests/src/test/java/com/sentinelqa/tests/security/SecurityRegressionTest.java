package com.sentinelqa.tests.security;

import static com.sentinelqa.support.Shop.ALICE;
import static com.sentinelqa.support.Shop.BOB;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.api.Api;
import com.sentinelqa.support.Shop;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Deterministic regression tests for vulnerabilities the AI pentest (Strix) and ZAP can find.
 * Scanners discover; these tests make sure a fixed hole never silently reopens.
 */
@Test(groups = "security")
public class SecurityRegressionTest {

    // ---- A03 Injection --------------------------------------------------------------------------------------

    @DataProvider
    public Object[][] sqlInjectionPayloads() {
        return new Object[][] {
                {"UNION exfiltration", "zzz') UNION SELECT id, username, password_hash, 0, 0 FROM users --"},
                {"tautology", "' OR '1'='1"},
                {"comment truncation", "') OR 1=1 --"},
                {"error-based cast", "') AND 1=CAST((SELECT password_hash FROM users LIMIT 1) AS int) --"},
        };
    }

    @Test(dataProvider = "sqlInjectionPayloads", description = "Product search treats SQL injection payloads as plain text")
    public void searchResistsSqlInjection(String technique, String payload) {
        Response response = Api.given().queryParam("q", payload).get("/api/products");

        assertEquals(response.statusCode(), 200, technique + ": payload should be treated as plain text");
        assertFalse(response.asString().contains("$2a$"), technique + ": password hashes leaked: " + response.asString());
        assertEquals(response.jsonPath().getList("$").size(), 0, technique + ": no product is named like the payload");
    }

    @Test(description = "Search input is HTML-escaped on the page (no reflected XSS)")
    public void searchIsEscapedOnPage() {
        String script = "<script>alert(1)</script>"; // no quotes: this test must only detect XSS, not the SQL injection defect

        Api.given().cookie("JSESSIONID", Shop.webSession(ALICE)).queryParam("q", script).get("/products")
                .then().statusCode(200)
                .body(not(containsString(script)))
                .body(containsString("&lt;script&gt;"));
    }

    // ---- A01 Broken access control --------------------------------------------------------------------------

    @Test(description = "A customer cannot read another customer's order via the API (IDOR)")
    public void ordersAreIsolatedBetweenCustomers() {
        long aliceOrder = Shop.placeOrder(ALICE, "Notebook", 1).then().statusCode(201).extract().jsonPath().getLong("id");

        Api.as(BOB.name(), BOB.password()).get("/api/orders/{id}", aliceOrder)
                .then().statusCode(404);
    }

    @Test(description = "A customer cannot open another customer's order page in the web UI (IDOR)")
    public void orderPagesAreIsolatedBetweenCustomers() {
        long aliceOrder = Shop.placeOrder(ALICE, "Notebook", 1).then().statusCode(201).extract().jsonPath().getLong("id");

        Api.given().cookie("JSESSIONID", Shop.webSession(BOB)).get("/orders/{id}", aliceOrder)
                .then().statusCode(404);
    }

    @DataProvider
    public Object[][] protectedPages() {
        return new Object[][] {{"/products"}, {"/cart"}, {"/orders"}, {"/orders/1"}};
    }

    @Test(dataProvider = "protectedPages", description = "Shop pages redirect anonymous visitors to login, without a session id in the URL")
    public void protectedPagesRequireLogin(String path) {
        Api.given().redirects().follow(false).get(path)
                .then().statusCode(302)
                .header("Location", not(containsString("jsessionid"))) // CWE-598: session id in URL
                .header("Location", endsWith("/login"));
    }

    // ---- A07 Identification and authentication failures ----------------------------------------------------

    @Test(description = "The session cookie is HttpOnly and SameSite=Lax")
    public void sessionCookieIsHardened() {
        Response login = Api.given().contentType(ContentType.URLENC).redirects().follow(false)
                .formParam("username", ALICE.name()).formParam("password", ALICE.password())
                .post("/login");

        String cookie = login.header("Set-Cookie");
        assertNotNull(cookie, "login must issue a session cookie");
        assertTrue(cookie.contains("HttpOnly"), "cookie readable by JavaScript: " + cookie);
        assertTrue(cookie.contains("SameSite=Lax"), "cookie without SameSite: " + cookie);
    }

    @Test(description = "Logging in issues a new session id (no session fixation)")
    public void loginRotatesSessionId() {
        String before = Api.given().get("/login").cookie("JSESSIONID");
        assertNotNull(before, "the login page should start a session");

        String after = Api.given().contentType(ContentType.URLENC).redirects().follow(false).cookie("JSESSIONID", before)
                .formParam("username", ALICE.name()).formParam("password", ALICE.password())
                .post("/login").cookie("JSESSIONID");

        assertNotNull(after, "login must issue a new session id");
        assertNotEquals(after, before, "session id must change on login");
    }

    @Test(description = "After logout the old session cookie no longer works")
    public void logoutInvalidatesSession() {
        String session = Shop.webSession(BOB);

        Api.given().cookie("JSESSIONID", session).redirects().follow(false).post("/logout").then().statusCode(302);

        Api.given().cookie("JSESSIONID", session).redirects().follow(false).get("/products")
                .then().statusCode(302)
                .header("Location", endsWith("/login"));
    }

    // ---- A05 Security misconfiguration ----------------------------------------------------------------------

    @DataProvider
    public Object[][] pages() {
        return new Object[][] {{"/login"}, {"/api/products"}, {"/actuator/health"}};
    }

    @Test(dataProvider = "pages", description = "Every response sends browser hardening headers (clickjacking, MIME sniffing, CSP)")
    public void securityHeadersPresent(String path) {
        Response page = Api.given().get(path);

        assertEquals(page.header("X-Content-Type-Options"), "nosniff", path);
        assertEquals(page.header("X-Frame-Options"), "DENY", path);
        assertEquals(page.header("Referrer-Policy"), "no-referrer", path);
        String csp = page.header("Content-Security-Policy");
        assertNotNull(csp, path + ": Content-Security-Policy header missing");
        assertTrue(csp.contains("frame-ancestors 'none'"), path + ": CSP allows framing: " + csp);
    }

    @DataProvider
    public Object[][] badRequests() {
        return new Object[][] {
                {"non-numeric id", "GET", "/api/products/abc", null},
                {"malformed JSON", "POST", "/api/auth/login", "{\"username\": "},
        };
    }

    @Test(dataProvider = "badRequests", description = "Error responses never leak stack traces or class names")
    public void errorsDoNotLeakInternals(String scenario, String method, String path, String body) {
        var request = Api.given();
        if (body != null) request.body(body);
        String response = request.request(method, path).then().statusCode(400).extract().asString();

        assertFalse(response.contains("\"trace\""), scenario + ": stack trace in response");
        assertFalse(response.matches("(?s).*\\bat (com|org|java)\\..*"), scenario + ": stack frame in response");
        assertFalse(response.contains("Exception"), scenario + ": exception class name in response: " + response);
    }
}
