@audit
Feature: Audit trail
  Every run is persisted locally so researchers can revisit past datasets and
  predictions without re-scraping

  Scenario: A completed run is stored for later review
    Given the app has finished a scan
    When the scan is marked complete with a verdict
    Then the audit trail should list the run with its verdict
    And the stored dataset should be readable offline
