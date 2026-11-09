package com.lucknow.analysis

/** Builds reports from the same JSON contract the Python engine emits. */
internal fun sampleReport(
    flawed: Boolean = false,
    biased: Boolean = false,
    chiP: Double = 0.42,
    acfP: Double = 0.31,
    length: Int = 120,
): AnalysisReport {
    val significant = if (flawed) {
        """[{"lag":1,"coefficient":0.62,"p_value":$acfP}]"""
    } else {
        "[]"
    }
    val firstLag = if (flawed) "1" else "null"
    val json = """
        {
          "ok": true,
          "engine": "lucknow-python",
          "library_versions": {"numpy": "1.23.3", "scipy": "1.8.1", "statsmodels": "0.14.5"},
          "sequence": {
            "length": $length, "min": 1, "max": 6,
            "mean": 3.5, "stddev": 1.71, "distinct": 6
          },
          "chi_square": {
            "statistic": 9.8, "p_value": $chiP, "degrees_of_freedom": 5,
            "support": {"min": 1, "max": 6, "distinct": 6},
            "expected_per_bucket": 20.0, "min_expected_count": 20.0,
            "reliable": true, "alpha": 0.05, "biased": $biased,
            "counts": [18, 21, 19, 22, 20, 20], "labels": [1, 2, 3, 4, 5, 6]
          },
          "autocorrelation": {
            "alpha": 0.05, "max_lag": 20,
            "lags": [0, 1, 2],
            "coefficients": [1.0, 0.62, -0.04],
            "ci_lower": [-0.18, -0.18, -0.18],
            "ci_upper": [0.18, 0.18, 0.18],
            "p_values": [0.0, $acfP, 0.61],
            "significant_lags": $significant,
            "first_significant_lag": $firstLag,
            "strongest": {"lag": 1, "coefficient": 0.62},
            "ljung_box": {"lag": 10, "statistic": 24.2, "p_value": 0.007},
            "flawed": $flawed,
            "estimator": "statsmodels.tsa.stattools.acf",
            "fallback_estimator": false
          },
          "warnings": [],
          "verdict": "no_evidence_of_bias",
          "computed_in_ms": 38
        }
    """.trimIndent()
    return AnalysisReport.fromJson(json)
}
