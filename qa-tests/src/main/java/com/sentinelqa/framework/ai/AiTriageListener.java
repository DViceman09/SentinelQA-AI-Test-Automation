package com.sentinelqa.framework.ai;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import com.sentinelqa.framework.ui.DriverFactory;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;

/**
 * Runs on every failed test (registered through META-INF/services, no XML needed):
 * attaches a browser screenshot, then asks the LLM to classify the failure so whoever reads the
 * report starts from "product bug vs test bug vs environment" instead of a raw stack trace.
 * It runs right after the test method, before Allure closes the result, so attachments land on the test.
 */
public class AiTriageListener implements IInvokedMethodListener {

    interface Triager {
        @SystemMessage("""
                You are a senior QA engineer triaging an automated test failure for a web shop.
                The suite has Selenium UI tests, REST Assured API tests, JDBC database tests and security tests.
                Classify the root cause:
                PRODUCT_BUG = the application behaves wrongly; TEST_BUG = the test code, data or locator is wrong;
                ENVIRONMENT = app, database, browser or network unavailable; FLAKY = timing or ordering issue.
                Be concrete and brief.""")
        @UserMessage("""
                Test: {{test}}
                Description: {{description}}
                Groups: {{groups}}
                Failure:
                {{failure}}""")
        Triage triage(@V("test") String test, @V("description") String description,
                      @V("groups") String groups, @V("failure") String failure);
    }

    public enum Category { PRODUCT_BUG, TEST_BUG, ENVIRONMENT, FLAKY }

    public record Triage(Category category, int confidencePercent, String summary, String suggestedNextStep) {}

    static final Path REPORT = Path.of("target", "ai-triage-report.md");

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod() || result.getStatus() != ITestResult.FAILURE) return;
        attachScreenshot();
        if (!Ai.enabled()) return;
        String test = result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
        try {
            Triage t = Ai.service(Triager.class).triage(test, String.valueOf(result.getMethod().getDescription()),
                    Arrays.toString(result.getMethod().getGroups()), failureText(result.getThrowable()));
            Allure.attachment("AI failure triage", "Category: %s (%d%% confident)%nSummary: %s%nNext step: %s"
                    .formatted(t.category(), t.confidencePercent(), t.summary(), t.suggestedNextStep()));
            MarkdownReport.appendRow(REPORT, "| Test | Category | Confidence | Summary | Suggested next step |",
                    test, String.valueOf(t.category()), t.confidencePercent() + "%", t.summary(), t.suggestedNextStep());
        } catch (RuntimeException e) {
            System.err.println("[ai-triage] skipped for " + test + ": " + e.getMessage()); // AI must never break the run
        }
    }

    private static void attachScreenshot() {
        DriverFactory.current().ifPresent(driver -> {
            try {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.attachment("Screenshot on failure", "image/png", new ByteArrayInputStream(png), AttachmentOptions.withFileExtension("png"));
            } catch (RuntimeException e) {
                System.err.println("[screenshot] failed: " + e.getMessage());
            }
        });
    }

    /** Message plus the first stack frames: enough signal for triage, small enough for free-tier models. */
    private static String failureText(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString().lines().limit(25).reduce("", (a, b) -> a + b + "\n");
    }
}
