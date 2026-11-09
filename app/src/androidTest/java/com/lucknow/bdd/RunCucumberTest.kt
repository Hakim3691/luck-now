package com.lucknow.bdd

import io.cucumber.junit.CucumberOptions

/**
 * Entry point for the BDD suite. `cucumber-android` discovers this
 * `@CucumberOptions` class through `CucumberAndroidJUnitRunner`; the feature
 * files live in `src/androidTest/assets/features`.
 */
@CucumberOptions(
    features = ["features"],
    glue = ["com.lucknow.bdd.steps"],
    plugin = ["pretty", "summary"],
    tags = "not @wip",
)
class RunCucumberTest
