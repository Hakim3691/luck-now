@interception
Feature: Dynamic data interception
  As a quantitative researcher
  I want Luck Now to capture the JSON that a lottery page loads in the background
  So that DOM changes cannot break data collection

  Scenario: Intercepting infinite scroll data from a URL
    Given the user launches "Luck Now"
    And a lottery results site that paginates with infinite scroll
    When the user enters the lottery site URL
    And taps the "Analyze" button
    Then the app should load the URL in a hidden WebView
    And intercept all incoming "application/json" payloads
    And pass the intercepted JSON to the parsing module
