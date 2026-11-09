"""Host-side verification of the on-device statistical engine.

Runs the exact module that Chaquopy ships (`app/src/main/python/lucknow/analysis.py`)
against CPython 3.10 with the same pinned library versions as the APK.
"""

from __future__ import annotations

import hashlib
import json
import random
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

from lucknow import analysis  # noqa: E402


def run(values, **options) -> dict:
    return json.loads(analysis.analyze(json.dumps(values), json.dumps(options)))


def loaded_machine_sequence(prefix: str = "lucknow-loaded-27", size: int = 200) -> list:
    """Mirror of the BDD fixture in ``AnalysisSteps.skewedSequence``.

    Ball 1 comes up about half the time, the other balls share the rest. The
    order is SHA-256 mixed, so the fixture models a machine that skews the
    distribution without also skewing the order.
    """
    values = []
    for index in range(size):
        digest = hashlib.sha256(f"{prefix}-{index}".encode()).digest()
        draw = int.from_bytes(digest[:2], "big")
        values.append(1 if draw < 32768 else 2 + draw % 5)
    return values


class ChiSquareTests(unittest.TestCase):
    def test_uniform_sequence_is_not_flagged(self):
        rng = random.Random(11)
        values = [rng.randint(1, 6) for _ in range(900)]
        report = run(values)
        self.assertTrue(report["ok"])
        self.assertTrue(report["chi_square"]["reliable"])
        self.assertGreater(report["chi_square"]["p_value"], 0.05)
        self.assertFalse(report["chi_square"]["biased"])

    def test_biased_machine_is_flagged(self):
        values = loaded_machine_sequence()
        report = run(values)
        self.assertTrue(report["chi_square"]["biased"])
        self.assertLess(report["chi_square"]["p_value"], 0.05)
        self.assertEqual(report["verdict"], "biased")
        codes = [warning["code"] for warning in report["warnings"]]
        self.assertIn("MECHANICAL_BIAS_DETECTED", codes)

    def test_biased_machine_fixture_does_not_look_like_a_prng_flaw(self):
        """The bias headline must not be hidden behind a PRNG finding.

        A block-ordered skewed fixture is also perfectly autocorrelated, so the
        engine reports both codes and the UI shows the PRNG verdict instead.
        The BDD fixture is serially mixed for exactly this reason.
        """
        report = run(loaded_machine_sequence())
        self.assertLess(report["chi_square"]["p_value"], 1e-10)
        self.assertEqual(report["autocorrelation"]["significant_lags"], [])
        self.assertEqual(report["verdict"], "biased")
        codes = [warning["code"] for warning in report["warnings"]]
        self.assertIn("MECHANICAL_BIAS_DETECTED", codes)
        self.assertNotIn("PRNG_FLAW_DETECTED", codes)

    def test_counts_match_observed_support(self):
        values = [1, 1, 2, 3, 3, 3, 4]
        report = run(values, alpha=0.05)
        chi = report["chi_square"]
        self.assertEqual(sum(chi["counts"]), len(values))
        self.assertEqual(chi["support"]["min"], 1)
        self.assertEqual(chi["support"]["max"], 4)
        self.assertEqual(len(chi["counts"]), 4)
        self.assertEqual(chi["degrees_of_freedom"], 3)

    def test_small_expected_counts_are_marked_unreliable(self):
        rng = random.Random(3)
        values = [rng.randint(1, 60) for _ in range(80)]
        report = run(values)
        self.assertFalse(report["chi_square"]["reliable"])
        codes = [warning["code"] for warning in report["warnings"]]
        self.assertIn("INSUFFICIENT_SAMPLE", codes)


class AutocorrelationTests(unittest.TestCase):
    def test_periodic_sequence_flags_its_cycle_length(self):
        values = [index % 6 + 1 for index in range(120)]
        report = run(values)
        autocorrelation = report["autocorrelation"]
        self.assertTrue(autocorrelation["flawed"])
        self.assertEqual(autocorrelation["first_significant_lag"], 6)
        # statsmodels' default (biased, divide-by-n) estimator caps just below 1
        # for a finite sample, even for a perfectly periodic sequence.
        self.assertGreater(autocorrelation["coefficients"][6], 0.9)
        self.assertLess(autocorrelation["p_values"][6], 0.05)

    def test_confidence_bounds_are_reported(self):
        rng = random.Random(5)
        values = [rng.randint(1, 6) for _ in range(300)]
        autocorrelation = run(values)["autocorrelation"]
        self.assertEqual(len(autocorrelation["lags"]), len(autocorrelation["coefficients"]))
        self.assertEqual(len(autocorrelation["coefficients"]), len(autocorrelation["ci_lower"]))
        self.assertEqual(len(autocorrelation["coefficients"]), len(autocorrelation["ci_upper"]))
        self.assertTrue(autocorrelation["estimator"].startswith("statsmodels"))
        # The Bartlett band under a white-noise null stays narrow for large n.
        self.assertLess(abs(autocorrelation["ci_upper"][1]), 0.2)

    def test_independent_sequence_has_no_lag_one_dependence(self):
        rng = random.Random(17)
        values = [rng.randint(1, 6) for _ in range(800)]
        autocorrelation = run(values)["autocorrelation"]
        self.assertGreater(autocorrelation["p_values"][1], 0.05)
        self.assertLess(abs(autocorrelation["coefficients"][1]), 0.1)

    def test_ljung_box_is_computed(self):
        rng = random.Random(23)
        values = [rng.randint(0, 9) for _ in range(400)]
        ljung_box = run(values)["autocorrelation"]["ljung_box"]
        self.assertIsNotNone(ljung_box)
        self.assertGreaterEqual(ljung_box["p_value"], 0.0)
        self.assertLessEqual(ljung_box["p_value"], 1.0)


class ValidationTests(unittest.TestCase):
    def test_rejects_empty_sequence(self):
        report = run([])
        self.assertFalse(report["ok"])
        self.assertEqual(report["error"]["code"], "ANALYSIS_FAILED")

    def test_rejects_non_integer_values(self):
        report = run([1, 2, 3.5, 4])
        self.assertFalse(report["ok"])
        self.assertIn("non-integer", report["error"]["message"])

    def test_rejects_too_short_sequences(self):
        report = run([1, 2])
        self.assertFalse(report["ok"])

    def test_rejects_invalid_alpha(self):
        report = run([1, 2, 3, 4, 5, 6], alpha=1.5)
        self.assertFalse(report["ok"])


class ContractTests(unittest.TestCase):
    def test_report_matches_the_kotlin_contract(self):
        values = [index % 6 + 1 for index in range(60)]
        report = run(values)
        self.assertTrue(report["ok"])
        self.assertEqual(report["engine"], "lucknow-python")
        for key in ("numpy", "scipy", "statsmodels"):
            self.assertIn(key, report["library_versions"])
        self.assertEqual(report["sequence"]["length"], 60)
        self.assertIn("chi_square", report)
        self.assertIn("autocorrelation", report)
        self.assertIn("warnings", report)
        self.assertIsInstance(report["computed_in_ms"], int)


if __name__ == "__main__":
    unittest.main(verbosity=2)
