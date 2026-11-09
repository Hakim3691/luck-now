"""Luck Now on-device analytical engine.

This module runs inside the app process through Chaquopy (no network, no
external services). It is deliberately dependency-light at import time so that
an interpreter failure can never take the Android UI down: every public entry
point returns a JSON document, including failure documents.

Public API
----------
analyze(values_json, options_json) -> str
    Statistically audit a 1-D integer sequence.

Statistical model
-----------------
* Chi-square goodness of fit against a discrete uniform distribution over the
  observed support ``[min, max]``. Used to detect physical/mechanical bias
  (loaded balls, uneven wheel segments, biased RNG rejection sampling).
* Autocorrelation (ACF) up to ``max_lag`` with Bartlett confidence bounds, plus
  a Ljung-Box portmanteau test. Weak PRNGs and badly seeded LCGs leave
  structure here even when the marginal distribution looks uniform.
* Per-lag p-values are derived from the asymptotic standard error of the ACF
  under the white-noise null (1/sqrt(n)).
"""

from __future__ import annotations

import json
import math
import time
from typing import Any, Dict, List, Optional

ENGINE_ID = "lucknow-python"
ALPHA_DEFAULT = 0.05
MAX_LAG_DEFAULT = 20
MIN_RELIABLE_EXPECTED = 5.0
SMALL_SAMPLE_THRESHOLD = 50


def _fail(message: str, code: str) -> str:
    return json.dumps(
        {
            "ok": False,
            "engine": ENGINE_ID,
            "error": {"code": code, "message": str(message)},
        }
    )


def _as_int_list(values_json: str) -> List[int]:
    parsed = json.loads(values_json)
    if not isinstance(parsed, list):
        raise ValueError("expected a JSON array of integers")
    values: List[int] = []
    for item in parsed:
        if isinstance(item, bool) or not isinstance(item, (int, float)):
            raise ValueError("sequence contains a non-numeric value: %r" % (item,))
        as_int = int(item)
        if as_int != item:
            raise ValueError("sequence contains a non-integer value: %r" % (item,))
        values.append(as_int)
    if not values:
        raise ValueError("sequence is empty")
    return values


def _chi_square(values: List[int], alpha: float) -> Dict[str, Any]:
    import numpy as np
    from scipy import stats

    arr = np.asarray(values, dtype=np.int64)
    low = int(arr.min())
    high = int(arr.max())
    support = high - low + 1
    counts = np.bincount(arr - low, minlength=support)

    expected = np.full(support, arr.size / float(support), dtype=float)
    result = stats.chisquare(counts.astype(float), expected)
    statistic = float(result.statistic)
    p_value = float(result.pvalue)
    min_expected = float(expected.min())

    return {
        "statistic": statistic,
        "p_value": p_value,
        "degrees_of_freedom": support - 1,
        "support": {"min": low, "max": high, "distinct": int(np.count_nonzero(counts))},
        "expected_per_bucket": float(expected[0]),
        "min_expected_count": min_expected,
        # The asymptotic chi-square approximation is only trustworthy when each
        # expected bucket has at least ~5 observations.
        "reliable": bool(min_expected >= MIN_RELIABLE_EXPECTED),
        "alpha": alpha,
        "biased": bool(p_value < alpha),
        "counts": [int(c) for c in counts.tolist()],
        "labels": list(range(low, high + 1)),
    }


def _manual_acf(values: List[float], max_lag: int) -> List[float]:
    """Fallback ACF (biased estimator, matching statsmodels' default)."""
    n = len(values)
    mean = sum(values) / n
    denominator = sum((v - mean) ** 2 for v in values)
    if denominator == 0.0:
        return [1.0] + [0.0] * max_lag
    coefficients = []
    for lag in range(max_lag + 1):
        numerator = 0.0
        for index in range(n - lag):
            numerator += (values[index] - mean) * (values[index + lag] - mean)
        coefficients.append(numerator / denominator)
    return coefficients


def _autocorrelation(values: List[int], max_lag: int, alpha: float) -> Dict[str, Any]:
    import numpy as np
    from scipy import stats

    x = np.asarray(values, dtype=float)
    n = int(x.size)
    lags = int(min(max_lag, max(1, n - 2)))
    used_fallback = False
    confidence: Optional[Any] = None

    try:
        from statsmodels.tsa.stattools import acf as statsmodels_acf

        coefficients, confidence = statsmodels_acf(
            x, nlags=lags, alpha=alpha, fft=False
        )
        coefficients = [float(c) for c in coefficients]
        ci_low = [float(c[0]) for c in confidence]
        ci_high = [float(c[1]) for c in confidence]
    except Exception:
        used_fallback = True
        coefficients = [float(c) for c in _manual_acf(list(x), lags)]
        z = float(stats.norm.ppf(1.0 - alpha / 2.0))
        bound = z / math.sqrt(n)
        ci_low = [-bound] * len(coefficients)
        ci_high = [bound] * len(coefficients)

    # Asymptotic standard error of the ACF under the white-noise null.
    standard_error = 1.0 / math.sqrt(n)
    p_values = []
    for coefficient in coefficients:
        z_score = abs(coefficient) / standard_error
        p_values.append(float(2.0 * stats.norm.sf(z_score)))

    significant = [
        {"lag": lag, "coefficient": coefficients[lag], "p_value": p_values[lag]}
        for lag in range(1, len(coefficients))
        if p_values[lag] < alpha
    ]
    significant.sort(key=lambda entry: entry["p_value"])

    ljung_box = _ljung_box(x, lags)

    return {
        "alpha": alpha,
        "max_lag": lags,
        "lags": list(range(len(coefficients))),
        "coefficients": coefficients,
        "ci_lower": ci_low,
        "ci_upper": ci_high,
        "p_values": p_values,
        "standard_error": standard_error,
        "significant_lags": significant,
        "first_significant_lag": significant[0]["lag"] if significant else None,
        "strongest": (
            max(
                (
                    {"lag": lag, "coefficient": coefficients[lag]}
                    for lag in range(1, len(coefficients))
                ),
                key=lambda entry: abs(entry["coefficient"]),
                default=None,
            )
        ),
        "ljung_box": ljung_box,
        "flawed": bool(significant),
        "fallback_estimator": used_fallback,
        "estimator": "statsmodels.tsa.stattools.acf" if not used_fallback else "numpy.manual",
    }


def _ljung_box(x, lags: int) -> Optional[Dict[str, Any]]:
    try:
        from statsmodels.stats.diagnostic import acorr_ljungbox

        lag = max(1, min(lags, max(1, len(x) // 5)))
        frame = acorr_ljungbox(x, lags=[lag], return_df=True)
        row = frame.iloc[0]
        return {
            "lag": int(lag),
            "statistic": float(row["lb_stat"]),
            "p_value": float(row["lb_pvalue"]),
        }
    except Exception:
        return None


def analyze(values_json: str, options_json: str = "{}") -> str:
    """Audit a sequence and return a JSON report.

    ``options_json`` accepts ``{"alpha": 0.05, "max_lag": 20,
    "expected_draws": 500}``. Unknown keys are ignored.
    """
    started = time.time()
    try:
        options = json.loads(options_json or "{}")
        alpha = float(options.get("alpha", ALPHA_DEFAULT))
        max_lag = int(options.get("max_lag", MAX_LAG_DEFAULT))
        if not 0.0 < alpha < 1.0:
            raise ValueError("alpha must be between 0 and 1")
        if max_lag < 1:
            raise ValueError("max_lag must be positive")

        values = _as_int_list(values_json)
        if len(values) < 3:
            raise ValueError("at least 3 draws are required for analysis")

        import numpy as np
        import scipy
        import statsmodels

        chi_square = _chi_square(values, alpha)
        autocorrelation = _autocorrelation(values, max_lag, alpha)

        warnings: List[Dict[str, str]] = []
        if chi_square["biased"]:
            warnings.append(
                {
                    "code": "MECHANICAL_BIAS_DETECTED",
                    "severity": "high",
                    "message": "Chi-square p=%.4f (< %.2f): the draw distribution is not uniform."
                    % (chi_square["p_value"], alpha),
                }
            )
        if autocorrelation["flawed"]:
            lag = autocorrelation["first_significant_lag"]
            warnings.append(
                {
                    "code": "PRNG_FLAW_DETECTED",
                    "severity": "high",
                    "message": "Autocorrelation at lag %d is significant (p=%.4f < %.2f): draws are not independent."
                    % (lag, autocorrelation["p_values"][lag], alpha),
                }
            )
        if not chi_square["reliable"]:
            warnings.append(
                {
                    "code": "INSUFFICIENT_SAMPLE",
                    "severity": "medium",
                    "message": "Expected count per bucket is below %.0f; chi-square may be unreliable."
                    % MIN_RELIABLE_EXPECTED,
                }
            )
        if len(values) < SMALL_SAMPLE_THRESHOLD:
            warnings.append(
                {
                    "code": "SMALL_SAMPLE",
                    "severity": "low",
                    "message": "%d draws is a small sample; treat findings as indicative."
                    % len(values),
                }
            )

        report = {
            "ok": True,
            "engine": ENGINE_ID,
            "library_versions": {
                "numpy": np.__version__,
                "scipy": scipy.__version__,
                "statsmodels": statsmodels.__version__,
            },
            "sequence": {
                "length": len(values),
                "min": int(min(values)),
                "max": int(max(values)),
                "mean": float(np.mean(values)),
                "stddev": float(np.std(values, ddof=1)) if len(values) > 1 else 0.0,
                "distinct": int(len(set(values))),
                "signature": values[:12],
            },
            "chi_square": chi_square,
            "autocorrelation": autocorrelation,
            "warnings": warnings,
            "verdict": (
                "biased"
                if chi_square["biased"]
                else "autocorrelated"
                if autocorrelation["flawed"]
                else "no_evidence_of_bias"
            ),
            "computed_in_ms": int((time.time() - started) * 1000),
        }
        return json.dumps(report)
    except Exception as error:  # noqa: BLE001 - boundary of the Python engine
        return _fail(str(error), "ANALYSIS_FAILED")
