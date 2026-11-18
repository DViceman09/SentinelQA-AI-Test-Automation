package com.sentinelqa.tests.api;

import static com.sentinelqa.support.Shop.ALICE;
import static com.sentinelqa.support.Shop.BOB;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sentinelqa.framework.api.Api;

import io.restassured.specification.RequestSpecification;

@Test(groups = "api")
public class AuthApiTest {

    @Test(description = "Valid credentials return a bearer token and the username, nothing else")
    public void validLoginReturnsToken() {
        Api.given().body(Map.of("username", ALICE.name(), "password", ALICE.password()))
                .post("/api/auth/login")
                .then().statusCode(200)
                .body("$", aMapWithSize(2))
                .body("token", matchesPattern("[0-9a-f-]{36}"))
                .body("username", equalTo(ALICE.name()));
    }

    @DataProvider
    public Object[][] wrongCredentials() {
        return new Object[][] {
                {"wrong password", ALICE.name(), "nope"},
                {"unknown user", "mallory", "mallory123"},
                {"username is case-sensitive", "ALICE", ALICE.password()},
                {"another user's password", ALICE.name(), BOB.password()},
                {"SQL tautology as username", "' OR '1'='1", "x"},
        };
    }

    @Test(dataProvider = "wrongCredentials", description = "Wrong credentials are rejected with 401, no token, and one generic message")
    public void wrongCredentialsAreRejected(String scenario, String username, String password) {
        Api.given().body(Map.of("username", username, "password", password))
                .post("/api/auth/login")
                .then().statusCode(401)
                .body("token", emptyOrNullString())
                // Same message for unknown user and wrong password, so attackers can't enumerate accounts.
                .body("message", equalTo("Invalid username or password"));
    }

    @DataProvider
    public Object[][] incompleteLogins() {
        return new Object[][] {
                {"missing password", map("username", ALICE.name())},
                {"missing username", map("password", ALICE.password())},
                {"blank username", map("username", "   ", "password", ALICE.password())},
                {"empty password", map("username", ALICE.name(), "password", "")},
                {"empty body", map()},
        };
    }

    @Test(dataProvider = "incompleteLogins", description = "Incomplete login requests are 400 validation errors, never 500")
    public void incompleteLoginIsBadRequest(String scenario, Map<String, String> body) {
        Api.given().body(body).post("/api/auth/login").then().statusCode(400);
    }

    @Test(description = "Every login issues a new, unique token")
    public void tokensAreUniquePerLogin() {
        assertNotEquals(Api.token(ALICE.name(), ALICE.password()), Api.token(ALICE.name(), ALICE.password()));
    }

    @Test(description = "A freshly issued token grants access to the customer's own data")
    public void tokenGrantsAccess() {
        Api.as(ALICE.name(), ALICE.password()).get("/api/orders").then().statusCode(200);
    }

    @DataProvider
    public Object[][] badAuthHeaders() {
        return new Object[][] {
                {"no header", null},
                {"empty bearer", "Bearer "},
                {"made-up token", "Bearer 00000000-0000-0000-0000-000000000000"},
                {"basic auth instead of bearer", "Basic YWxpY2U6YWxpY2UxMjM="},
                {"token without the Bearer prefix", "TOKEN_ONLY"},
        };
    }

    @Test(dataProvider = "badAuthHeaders", description = "Protected endpoints reject missing or invalid credentials with 401")
    public void protectedEndpointRejectsBadAuth(String scenario, String header) {
        RequestSpecification request = Api.given();
        if ("TOKEN_ONLY".equals(header)) header = Api.token(ALICE.name(), ALICE.password());
        if (header != null) request.header("Authorization", header);
        request.get("/api/orders").then().statusCode(401);
    }

    @Test(description = "The login response never contains the password hash")
    public void loginDoesNotLeakPasswordHash() {
        Api.given().body(Map.of("username", ALICE.name(), "password", ALICE.password()))
                .post("/api/auth/login")
                .then().statusCode(200)
                .body(not(containsString("$2a$")));
    }

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);
        return map;
    }
}
