package com.sentinelqa.tests.ui;

import org.testng.annotations.AfterMethod;

import com.sentinelqa.framework.ui.DriverFactory;

public abstract class BaseUiTest {

    // Runs after the failure listener, so the screenshot is taken before the browser closes.
    @AfterMethod(alwaysRun = true)
    public void closeBrowser() {
        DriverFactory.quit();
    }
}
