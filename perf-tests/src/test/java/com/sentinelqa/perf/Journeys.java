package com.sentinelqa.perf;

import static us.abstracta.jmeter.javadsl.JmeterDsl.httpDefaults;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.jsonExtractor;

import org.apache.http.entity.ContentType;

import us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup.ThreadGroupChild;
import us.abstracta.jmeter.javadsl.http.DslHttpDefaults;

/** The user journey both load and stress tests replay: log in, browse, search, buy. */
final class Journeys {

    static final String BASE_URL = System.getProperty("base.url",
            System.getenv().getOrDefault("BASE_URL", "http://localhost:8080"));

    private Journeys() {}

    static DslHttpDefaults defaults() {
        return httpDefaults().url(BASE_URL);
    }

    /** Uses the dedicated 'shopper' account and high-stock 'Sticker Pack' so perf traffic never disturbs functional test data. */
    static ThreadGroupChild[] shopping() {
        return new ThreadGroupChild[] {
                httpSampler("login", "/api/auth/login")
                        .post("{\"username\":\"shopper\",\"password\":\"shopper123\"}", ContentType.APPLICATION_JSON)
                        .children(jsonExtractor("TOKEN", "token")),
                httpSampler("browse catalogue", "/api/products"),
                httpSampler("search", "/api/products?q=sticker")
                        .children(jsonExtractor("PRODUCT_ID", "[0].id")),
                httpSampler("place order", "/api/orders")
                        .header("Authorization", "Bearer ${TOKEN}")
                        .post("{\"items\":[{\"productId\":${PRODUCT_ID},\"quantity\":1}]}", ContentType.APPLICATION_JSON),
        };
    }

    static int intProp(String name, int defaultValue) {
        return Integer.getInteger(name, defaultValue);
    }
}
