@offline
Feature: Offline processing
  All parsing and statistical testing happens on-device so scraped data never
  leaves the phone and no external API is billed

  Scenario: Statistical analysis runs entirely on the device
    Given a normalized array of historical draw integers that is well mixed
    When the statistical analysis is executed offline
    Then the report should come from the embedded Python engine
    And the report should include the local scipy and statsmodels versions
