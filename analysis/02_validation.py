"""
02_validation.py
================
Validation metrics for the Queueing-Based Resource Saturation Model.

Computes:
  • MAE, RMSE, MAPE, R², Pearson r  for latency and queue predictions
  • 2 000-iteration bootstrap 95% CI for MAPE and RMSE
  • Shapiro-Wilk normality test → paired t-test or Wilcoxon signed-rank
  • Exports:
      paper/validation_metrics.csv
      paper/table2_validation_metrics.tex
      paper/table3_statistical_tests.tex

Run standalone:
    python 02_validation.py
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

import numpy as np
import pandas as pd
from scipy import stats

from load_01 import load_all, OUTPUT_DIR   # noqa: E402  (after sys.path fix)


# ── Metric functions ─────────────────────────────────────────────────────────

def compute_metrics(pred, meas, label=""):
    """Return dict of validation metrics for paired (pred, meas) arrays."""
    pred = np.asarray(pred, dtype=float)
    meas = np.asarray(meas, dtype=float)
    ok   = np.isfinite(pred) & np.isfinite(meas)
    pred, meas = pred[ok], meas[ok]
    n = len(pred)

    mae  = float(np.mean(np.abs(meas - pred)))
    rmse = float(np.sqrt(np.mean((meas - pred) ** 2)))

    # MAPE — guard against zero denominator
    nonzero = pred != 0
    mape = float(np.mean(np.abs((meas[nonzero] - pred[nonzero]) / pred[nonzero])) * 100) \
        if nonzero.any() else np.nan

    # R²
    ss_res = float(np.sum((meas - pred) ** 2))
    ss_tot = float(np.sum((meas - np.mean(meas)) ** 2))
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else np.nan

    # Pearson
    if n >= 3:
        r_val, p_r = stats.pearsonr(pred, meas)
    else:
        r_val, p_r = np.nan, np.nan

    # Mean bias  (positive = model under-predicts)
    bias = float(np.mean(meas - pred))

    return dict(label=label, n=n, MAE=mae, RMSE=rmse,
                MAPE=mape, R2=r2, r=r_val, p_pearson=p_r, bias=bias)


def bootstrap_ci(pred, meas, metric_fn, n_boot=2000, seed=42):
    """Percentile bootstrap 95% CI for a scalar metric."""
    rng  = np.random.default_rng(seed)
    pred = np.asarray(pred, dtype=float)
    meas = np.asarray(meas, dtype=float)
    ok   = np.isfinite(pred) & np.isfinite(meas)
    pred, meas = pred[ok], meas[ok]
    n    = len(pred)
    vals = []
    for _ in range(n_boot):
        idx = rng.integers(0, n, size=n)
        vals.append(metric_fn(pred[idx], meas[idx]))
    vals = np.array(vals)
    return float(np.percentile(vals, 2.5)), float(np.percentile(vals, 97.5))


def mape_fn(p, m):
    nz = p != 0
    return float(np.mean(np.abs((m[nz] - p[nz]) / p[nz])) * 100) if nz.any() else np.nan


def rmse_fn(p, m):
    return float(np.sqrt(np.mean((m - p) ** 2)))


# ── Statistical tests ────────────────────────────────────────────────────────

def paired_test(pred, meas, label):
    """Shapiro-Wilk → t-test or Wilcoxon. Returns summary dict."""
    pred = np.asarray(pred, dtype=float)
    meas = np.asarray(meas, dtype=float)
    diff = meas - pred
    n    = len(diff)
    sw_stat, sw_p = stats.shapiro(diff) if n >= 3 else (np.nan, np.nan)
    is_normal = sw_p > 0.05

    if is_normal:
        stat, pval = stats.ttest_rel(pred, meas)
        test_name  = "Paired t-test"
    else:
        stat, pval = stats.wilcoxon(pred, meas, alternative='two-sided')
        test_name  = "Wilcoxon signed-rank"

    return dict(
        label=label, n=n,
        normality_ok=is_normal, sw_p=float(sw_p),
        test=test_name, stat=float(stat), p_value=float(pval),
        significant=pval < 0.05,
        mean_bias=float(np.mean(diff)),
    )


# ── Main ─────────────────────────────────────────────────────────────────────

def run():
    D = load_all()
    av  = D['arr_val']
    pv  = D['pool_val']

    # Stable rows only (exclude rho=1.05)
    av_s = av[av['Rho_Target'] < 1.0].copy()
    vt   = av_s[av_s['Mode'] == 'VT']
    pt   = av_s[av_s['Mode'] == 'PT']

    # ── Latency ──────────────────────────────────────────────────────────────
    p_lat_vt = vt['Model_Mean_Latency_ms'].values
    m_lat_vt = vt['Measured_Mean_Latency_ms'].values
    p_lat_pt = pt['Model_Mean_Latency_ms'].values
    m_lat_pt = pt['Measured_Mean_Latency_ms'].values
    p_lat_all = np.concatenate([p_lat_vt, p_lat_pt])
    m_lat_all = np.concatenate([m_lat_vt, m_lat_pt])

    # ── Queue length ─────────────────────────────────────────────────────────
    p_nq_vt = vt['Model_E_Nq'].values
    m_nq_vt = vt['Measured_Nq'].values
    p_nq_pt = pt['Model_E_Nq'].values
    m_nq_pt = pt['Measured_Nq'].values

    # ── Heap (pool sweep, test pools only) ────────────────────────────────────
    p_heap = pv['Model_Heap_MB'].values
    m_heap = pv['Measured_Heap_MB'].values

    # ── Compute all metrics ───────────────────────────────────────────────────
    records = [
        compute_metrics(p_lat_vt,  m_lat_vt,  "Mean Latency — VT"),
        compute_metrics(p_lat_pt,  m_lat_pt,  "Mean Latency — PT"),
        compute_metrics(p_lat_all, m_lat_all, "Mean Latency — All"),
        compute_metrics(p_nq_vt,   m_nq_vt,   "Queue Length — VT"),
        compute_metrics(p_nq_pt,   m_nq_pt,   "Queue Length — PT"),
        compute_metrics(p_heap,    m_heap,    "Heap Memory (pool sweep)"),
    ]
    df_metrics = pd.DataFrame(records).set_index('label')
    print("\n=== VALIDATION METRICS ===")
    print(df_metrics[['n','MAE','RMSE','MAPE','R2','r','p_pearson','bias']].to_string())

    # ── Bootstrap CI ─────────────────────────────────────────────────────────
    print("\n=== BOOTSTRAP CI (latency, all modes, n=2000) ===")
    mape_lo, mape_hi = bootstrap_ci(p_lat_all, m_lat_all, mape_fn)
    rmse_lo, rmse_hi = bootstrap_ci(p_lat_all, m_lat_all, rmse_fn)
    mape_val = mape_fn(p_lat_all, m_lat_all)
    rmse_val = rmse_fn(p_lat_all, m_lat_all)
    print(f"  MAPE = {mape_val:.1f}%  95% CI [{mape_lo:.1f} – {mape_hi:.1f}%]")
    print(f"  RMSE = {rmse_val:.1f} ms  95% CI [{rmse_lo:.1f} – {rmse_hi:.1f} ms]")

    # ── Statistical tests ─────────────────────────────────────────────────────
    print("\n=== STATISTICAL TESTS ===")
    test_records = [
        paired_test(p_lat_vt,  m_lat_vt,  "Latency VT"),
        paired_test(p_lat_pt,  m_lat_pt,  "Latency PT"),
        paired_test(p_nq_vt,   m_nq_vt,   "Queue VT"),
        paired_test(p_nq_pt,   m_nq_pt,   "Queue PT"),
    ]
    df_tests = pd.DataFrame(test_records).set_index('label')
    print(df_tests[['n','test','stat','p_value','significant','mean_bias']].to_string())

    # ── Per-rho breakdown ─────────────────────────────────────────────────────
    print("\n=== PER-RHO VALIDATION (VT) ===")
    for _, row in vt.iterrows():
        err = row['Latency_Error_Pct']
        print(f"  ρ={row['Rho_Target']:.2f}  pred={row['Model_Mean_Latency_ms']:.1f} ms"
              f"  meas={row['Measured_Mean_Latency_ms']:.1f} ms  error={err:.1f}%"
              f"  Nq_err={row['Nq_Error_Pct']:.1f}%  region={row['Model_Region']}")

    # ── Export CSV ────────────────────────────────────────────────────────────
    df_metrics.round(4).to_csv(OUTPUT_DIR / "validation_metrics.csv")
    df_tests.round(4).to_csv(OUTPUT_DIR / "statistical_tests.csv")
    print(f"\n✓ Saved: {OUTPUT_DIR}/validation_metrics.csv")
    print(f"✓ Saved: {OUTPUT_DIR}/statistical_tests.csv")

    # ── LaTeX tables ─────────────────────────────────────────────────────────
    _write_table2_latex(df_metrics, mape_val, mape_lo, mape_hi,
                        rmse_val,  rmse_lo,  rmse_hi)
    _write_table3_latex(df_tests)

    return df_metrics, df_tests, {
        'mape_val': mape_val, 'mape_lo': mape_lo, 'mape_hi': mape_hi,
        'rmse_val': rmse_val, 'rmse_lo': rmse_lo, 'rmse_hi': rmse_hi,
    }


# ── LaTeX helpers ─────────────────────────────────────────────────────────────

def _write_table2_latex(df, mape_val, mape_lo, mape_hi,
                        rmse_val, rmse_lo, rmse_hi):
    rows = [
        "Mean Latency — VT",
        "Mean Latency — PT",
        "Mean Latency — All",
        "Queue Length — VT",
        "Queue Length — PT",
        "Heap Memory (pool sweep)",
    ]
    lines = [
        r"\begin{table}[ht]",
        r"\centering",
        r"\caption{Validation metrics comparing Erlang-C model predictions to measured "
        r"values. MAE: Mean Absolute Error; RMSE: Root Mean Squared Error; "
        r"MAPE: Mean Absolute Percentage Error; $R^2$: coefficient of determination; "
        r"$r$: Pearson correlation. Bootstrap 95\% CI uses 2\,000 resamples.}",
        r"\label{tab:validation}",
        r"\begin{tabular}{lrrrrrr}",
        r"\toprule",
        r"\textbf{Metric} & $n$ & MAE & RMSE & MAPE (\%) & $R^2$ & $r$ \\",
        r"\midrule",
    ]
    units = {
        "Mean Latency — VT":       "ms",
        "Mean Latency — PT":       "ms",
        "Mean Latency — All":      "ms",
        "Queue Length — VT":       "req",
        "Queue Length — PT":       "req",
        "Heap Memory (pool sweep)":"MB",
    }
    for r in rows:
        if r not in df.index:
            continue
        m = df.loc[r]
        u = units.get(r, "")
        p_str = f"{m['p_pearson']:.3f}" if not np.isnan(m['p_pearson']) else "--"
        lines.append(
            f"  {r} ({u}) & {int(m['n'])} & {m['MAE']:.1f} & {m['RMSE']:.1f}"
            f" & {m['MAPE']:.1f} & {m['R2']:.3f} & {m['r']:.3f} \\\\"
        )
    lines += [
        r"\midrule",
        r"\multicolumn{7}{l}{\footnotesize "
        rf"Bootstrap 95\% CI (latency, all): "
        rf"MAPE = {mape_val:.1f}\% [{mape_lo:.1f}--{mape_hi:.1f}\%]; "
        rf"RMSE = {rmse_val:.1f}\,ms [{rmse_lo:.1f}--{rmse_hi:.1f}\,ms]"
        r"} \\",
        r"\bottomrule",
        r"\end{tabular}",
        r"\end{table}",
    ]
    path = OUTPUT_DIR / "table2_validation_metrics.tex"
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"✓ Saved: {path}")


def _write_table3_latex(df_tests):
    lines = [
        r"\begin{table}[ht]",
        r"\centering",
        r"\caption{Statistical significance of prediction error. "
        r"Normality assessed via Shapiro--Wilk; normal residuals use paired $t$-test, "
        r"non-normal use Wilcoxon signed-rank. Significance level $\alpha = 0.05$.}",
        r"\label{tab:stat_tests}",
        r"\begin{tabular}{llrrl}",
        r"\toprule",
        r"\textbf{Comparison} & \textbf{Test} & \textbf{Stat} & $p$ & \textbf{Sig.} \\",
        r"\midrule",
    ]
    for label, row in df_tests.iterrows():
        sig = r"\checkmark" if row['significant'] else "--"
        p_str = f"{row['p_value']:.4f}" if row['p_value'] >= 0.0001 else "$<$0.0001"
        lines.append(
            f"  {label} & {row['test']} & {row['stat']:.2f} & {p_str} & {sig} \\\\"
        )
    lines += [
        r"\bottomrule",
        r"\end{tabular}",
        r"\end{table}",
    ]
    path = OUTPUT_DIR / "table3_statistical_tests.tex"
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"✓ Saved: {path}")


if __name__ == "__main__":
    # Make sure 01_load.py is importable as 'load_01'
    import importlib, types
    spec   = importlib.util.spec_from_file_location("load_01", Path(__file__).parent / "01_load.py")
    mod    = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    sys.modules["load_01"] = mod
    from load_01 import load_all, OUTPUT_DIR  # noqa: F811
    run()
