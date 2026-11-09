@normalization
Feature: On-device AI normalization
  The bundled Qwen2.5-0.5B-Instruct model must turn arbitrary JSON schemas
  into a strict one-dimensional integer array

  Scenario: Normalizing JSON to an integer sequence
    Given a raw JSON string containing "draw_history" data
    When the data is passed to the Qwen 0.5B local LLM prompt
    Then the LLM should output a strict 1D array of integers
    And the array size must match the expected draw volume

  Scenario: Self-healing when the LLM returns prose
    Given the Qwen model returns prose on its first attempt
    When the extraction is retried
    Then the second prompt should contain the validation error
    And the final array should be valid

  Scenario: Falling back deterministically when the model is absent
    Given a raw JSON string containing "draw_history" data
    And no on-device LLM model is installed
    When the extraction engine is selected
    Then the deterministic parser should be used
    And the result should be flagged as degraded
