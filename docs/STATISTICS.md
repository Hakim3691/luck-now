# Statistical methodology

The engine is `app/src/main/python/lucknow/analysis.py`; it returns JSON consumed by
`AnalysisReport`. Every test is a null-hypothesis test at `alpha = 0.05` (configurable per call).

## Chi-square goodness of fit (mechanical bias)

A physically biased machine (loaded ball, uneven wheel segment, biased rejection sampling) produces a
non-uniform marginal distribution.

```
H0: every outcome in [min, max] is equally likely
statistic = Σ (O_i − E_i)² / E_i
E_i       = n / k            (k = max − min + 1 buckets)
dof       = k − 1
p         = P(χ²_k−1 ≥ statistic)          (scipy.stats.chisquare)
```

Guards and reporting:

* `min_expected_count = n / k`; when it is below 5 the asymptotic approximation is unreliable, so
  `reliable = false` and an `INSUFFICIENT_SAMPLE` warning is emitted.
* The observed support is the *observed* range, not a caller-supplied universe. A 1..49 lottery with
  only 6 drawn balls is treated as 49 buckets — pass enough draws so `E_i ≥ 5`.

## Autocorrelation (weak PRNG / serial dependence)

Weak PRNGs and badly seeded LCGs leave serial structure behind even when each outcome looks uniform.

```
H0: ρ_k = 0 for k ≥ 1
ACF_k = Σ_{t=1}^{n−k} (x_t − x̄)(x_{t+k} − x̄) / Σ_{t=1}^{n} (x_t − x̄)²
```

* `statsmodels.tsa.stattools.acf(nlags, alpha)` returns the coefficients and Bartlett confidence
  bounds; if statsmodels is unavailable a NumPy implementation with `±z_{1−α/2}/√n` bounds is used
  and reported via `estimator` / `fallback_estimator`.
* Per-lag p-values use the white-noise standard error `SE = 1/√n`:
  `z_k = |ACF_k| / SE`, `p_k = 2 · Φ̄(z_k)`.
* `first_significant_lag` is the smallest lag with `p < alpha`;
  `significant_lags` is sorted by ascending p-value.
* `Ljung-Box` (lag ≈ min(20, n/5)) is reported as a portmanteau check on the whole series.

## Verdict rules

| Condition | Warning | UI headline |
| --- | --- | --- |
| any `p_k < alpha` | `PRNG_FLAW_DETECTED` | *PRNG Flaw Detected* |
| chi-square `p < alpha` | `MECHANICAL_BIAS_DETECTED` | *Mechanical Bias Detected* |
| both | both warnings | PRNG flaw wins (serial dependence is the stronger finding) |
| neither | — | *No evidence of bias or PRNG flaws* |

Sample-size warnings (`SMALL_SAMPLE` under 50 draws, `INSUFFICIENT_SAMPLE` when `E_i < 5`) are always
reported so a "clean" verdict on a thin dataset is not over-read.

## Multiple comparisons

Twenty lags are tested at α = 0.05, so the family-wise false-positive rate is
`1 − 0.95²⁰ ≈ 64%`. Treat a single marginally significant lag with suspicion:

* `first_significant_lag` includes its p-value; anything near 0.05 deserves a larger sample.
* The Ljung-Box p-value is the better screen for "something is off overall".
* The BDD clean fixture is a SHA-256-mixed sequence verified to have no significant lag, so the
  suite does not depend on luck.

## What these tests cannot do

* They cannot prove a draw is fair, only fail to find evidence otherwise.
* They cannot detect a PRNG whose structure is invisible in the marginal/serial statistics of the
  extracted numbers (e.g. dependence on unobserved state, or mapping via a secret permutation).
* They are only as good as normalization: a mis-parsed sequence produces confident nonsense, which is
  why extraction reports its engine, attempt count and expected volume before analysis.
* Chi-square is sensitive to the bucket definition; with sparse data prefer descriptive inspection.
