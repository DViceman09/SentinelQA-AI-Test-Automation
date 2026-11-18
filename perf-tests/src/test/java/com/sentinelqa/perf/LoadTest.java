package com.sentinelqa.perf;

import static com.sentinelqa.perf.Journeys.intProp;
import static org.testng.Assert.assertTrue;
import static us.abstracta.jmeter.javadsl.JmeterDsl.htmlReporter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;

import java.time.Duration;

import org.testng.annotations.Test;

import us.abstracta.jmeter.javadsl.core.DslTestPlan;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

/**
 * Load test: the expected peak traffic must meet the SLA.
 * ./mvnw -pl perf-tests test -Dtest=LoadTest -Dperf.users=50 -Dperf.holdSeconds=120 -Dperf.p95Ms=800
 */
public class LoadTest {

    @Test(description = "Expected peak load meets the SLA: p95 latency and error rate")
    public void shoppingJourneyMeetsSla() throws Exception {
        int users = intProp("perf.users", 20);
        Duration p95Sla = Duration.ofMillis(intProp("perf.p95Ms", 800));
        double maxErrorRate = 0.01;

        DslTestPlan plan = testPlan(
                Journeys.defaults(),
                threadGroup("shoppers")
                        .rampToAndHold(users, Duration.ofSeconds(intProp("perf.rampSeconds", 10)),
                                Duration.ofSeconds(intProp("perf.holdSeconds", 30)))
                        .children(Journeys.shopping()),
                htmlReporter("target/jmeter-reports", "load-" + System.currentTimeMillis()));
        plan.saveAsJmx("target/load-test.jmx"); // same plan, openable in the JMeter GUI
        TestPlanStats stats = plan.run();

        StatsSummary all = stats.overall();
        double errorRate = (double) all.errorsCount() / Math.max(1, all.samplesCount());
        System.out.printf("%nLOAD %d users: %d requests, %.2f%% errors, p95 %d ms, %.1f req/s%n", users,
                all.samplesCount(), errorRate * 100, all.sampleTime().perc95().toMillis(),
                all.samplesCount() / Math.max(1.0, stats.duration().toMillis() / 1000.0));
        for (String label : stats.labels()) {
            System.out.printf("  %-18s p95 %5d ms  errors %d%n", label, stats.byLabel(label).sampleTime().perc95().toMillis(),
                    stats.byLabel(label).errorsCount());
        }

        assertTrue(errorRate <= maxErrorRate, "error rate %.2f%% exceeds 1%%".formatted(errorRate * 100));
        assertTrue(all.sampleTime().perc95().compareTo(p95Sla) <= 0,
                "p95 " + all.sampleTime().perc95().toMillis() + " ms exceeds SLA " + p95Sla.toMillis() + " ms");
    }
}
