@analysis
Feature: Mechanical bias detection
  A physically biased draw machine produces an uneven outcome distribution,
  which a chi-square goodness-of-fit test exposes

  Scenario: Detecting a mechanically biased draw machine
    Given a normalized array of historical draw integers that favour one ball
    When the Chi-Square test is executed
    Then the Chi-Square p-value should be below 0.05
    And the UI should highlight a "Mechanical Bias Detected" warning
