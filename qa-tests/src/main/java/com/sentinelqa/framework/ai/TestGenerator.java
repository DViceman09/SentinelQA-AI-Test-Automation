package com.sentinelqa.framework.ai;

import java.nio.file.Files;
import java.nio.file.Path;

import com.sentinelqa.framework.Config;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.restassured.RestAssured;

/**
 * Drafts a REST Assured + TestNG test class from the running app's OpenAPI spec.
 * Output goes to target/ai-generated for human review: AI writes the first draft, an engineer
 * decides what is worth keeping in src/test.
 * Run: ./mvnw -pl qa-tests compile exec:java
 */
public final class TestGenerator {

    interface Writer {
        @SystemMessage("""
                You are a senior SDET. Write ONE compilable Java 21 TestNG test class named GeneratedApiTest in
                package com.sentinelqa.generated. Use REST Assured (static io.restassured.RestAssured.given) and
                Hamcrest matchers. Base URI: System.getProperty("base.url", "http://localhost:8080").
                Authenticate with POST /api/auth/login body {"username":"alice","password":"alice123"} and send the
                returned token as "Authorization: Bearer <token>".
                For every endpoint cover the happy path and negative cases (no auth, invalid input, unknown ids).
                Give each @Test a description. Reply with Java code only, no markdown fences.""")
        String write(@UserMessage String openApiSpec);
    }

    public static void main(String[] args) throws Exception {
        if (!Ai.enabled()) {
            System.err.println("AI is off. Set AI_BASE_URL (plus AI_API_KEY / AI_MODEL) in .env first.");
            System.exit(1);
        }
        String spec = RestAssured.get(Config.baseUrl() + "/v3/api-docs").then().statusCode(200).extract().asString();
        String code = Ai.service(Writer.class).write(spec)
                .replaceAll("(?s)^\\s*```(?:java)?\\s*", "").replaceAll("(?s)\\s*```\\s*$", "");
        Path out = Path.of(Config.get("generator.out", "target/ai-generated"), "com", "sentinelqa", "generated", "GeneratedApiTest.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, code);
        System.out.println("Draft written to " + out.toAbsolutePath());
        System.out.println("Review it, then move it to src/test/java/com/sentinelqa/generated/ to keep it.");
    }
}
