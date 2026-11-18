package com.sentinelqa.perf;

import static com.sentinelqa.perf.Journeys.intProp;
import static org.testng.Assert.assertTrue;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import org.testng.annotations.Test;

import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

/**
 * Step stress test: raise concurrent users step by step until the app breaks
 * (errors over 5% or p95 over the limit) and report the last healthy level, i.e. the capacity.
 * ./mvnw -pl perf-tests test -Dtest=StressTest -Dstress.steps=10,25,50,100,200 -Dstress.holdSeconds=20
 */
public class StressTest {

    @Test(description = "Find the breaking point and require a minimum capacity")
    public void findBreakingPoint() throws Exception {
        int[] steps = Arrays.stream(System.getProperty("stress.steps", "10,25,50,100,150,200").split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
        Duration hold = Duration.ofSeconds(intProp("stress.holdSeconds", 20));
        Duration p95Limit = Duration.ofMillis(intProp("stress.p95Ms", 2000));
        double maxErrorRate = 0.05;

        StringBuilder report = new StringBuilder("""
                ## Stress test: breaking point
                | Users | Requests | Errors | p95 ms | Throughput req/s | Verdict |
                |---|---|---|---|---|---|
                """);
        int capacity = 0;
        Integer breakingPoint = null;
        for (int users : steps) {
            TestPlanStats stats = testPlan(Journeys.defaults(),
                    threadGroup("stress-" + users).rampToAndHold(users, Duration.ofSeconds(5), hold)
                            .children(Journeys.shopping())).run();
            StatsSummary all = stats.overall();
            double errorRate = (double) all.errorsCount() / Math.max(1, all.samplesCount());
            Duration p95 = all.sampleTime().perc95();
            boolean healthy = errorRate <= maxErrorRate && p95.compareTo(p95Limit) <= 0;
            report.append("| %d | %d | %.1f%% | %d | %.1f | %s |%n".formatted(users, all.samplesCount(), errorRate * 100,
                    p95.toMillis(), all.samplesCount() / Math.max(1.0, stats.duration().toMillis() / 1000.0),
                    healthy ? "healthy" : "**broken**"));
            if (!healthy) {
                breakingPoint = users;
                break;
            }
            capacity = users;
        }
        report.append("%nCapacity: **%d users** - Breaking point: **%s**%n"
                .formatted(capacity, breakingPoint == null ? "not reached up to " + steps[steps.length - 1] + " users" : breakingPoint + " users"));

        Path out = Path.of("target", "stress-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println(report);

        int minUsers = intProp("stress.minUsers", 10);
        assertTrue(capacity >= minUsers, "capacity " + capacity + " users is below the required " + minUsers);
    }
}
