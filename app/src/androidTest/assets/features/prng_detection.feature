@analysis
Feature: PRNG flaw detection
  Weak pseudo-random number generators leave serial structure behind even when
  each individual outcome looks equally likely

  Scenario: Detecting a flawed PRNG sequence
    Given a normalized array of historical draw integers with a repeating cycle
    When the Autocorrelation test is executed
    Then the system should calculate the ACF correlation coefficient
    And the ACF p-value should be below 0.05
    And the UI should highlight a "PRNG Flaw Detected" warning

  Scenario: A well-mixed sequence is not flagged as a flawed PRNG
    Given a normalized array of historical draw integers that is well mixed
    When the Autocorrelation test is executed
    Then the result should not flag a PRNG flaw
