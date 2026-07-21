"""
03_statistics.py
================
Extended statistical analysis:
  • Coefficient of variation per replicate group
  • Effect size VT vs PT:  Cohen's d  +  Cliff's delta (non-parametric)
  • Automatic operating-envelope breakpoint detection via piecewise regression
  • Correlation matrix from raw arrival sweep data

Exports:
    paper/table4_effect_size.tex
    paper/breakpoint_detection.csv
    paper/correlation_matrix.csv

Run standalone:
    python 03_statistics.py
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

import numpy as np
import pandas as pd
from scipy import stats
import importlib

# ── Dynamic import of 01_load ─────────────────────────────────────────────────
def _import_load():
    spec = importlib.util.spec_from_file_location(
        "load_01", Path(__file__).parent / "01_load.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    sys.modules["load_01"] = mod
    return mod

_mod = _import_load()
load_all   = _mod.load_all
OUTPUT_DIR = _mod.OUTPUT_DIR


# ── Effect-size functions ─────────────────────────────────────────────────────

def cohens_d(a, b):
    """Pooled Cohen's d for two independent samples."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    na, nb = len(a), len(b)
    pooled_std = np.sqrt(((na-1)*np.var(a, ddof=1) + (nb-1)*np.var(b, ddof=1)) / (na+nb-2))
    return (np.mean(a) - np.mean(b)) / pooled_std if pooled_std > 0 else np.nan


def cliffs_delta(a, b):
    """
    Cliff's delta: proportion of pairs where a > b minus proportion where a < b.
    Range [-1, 1];  |δ| < 0.147 negligible, < 0.33 small, < 0.474 medium, else large.
    """
    a, b = np.asarray(a, float), np.asarray(b, float)
    dominance = np.array([int(xi > yi) - int(xi < yi) for xi in a for yi in b])
    return float(np.mean(dominance))


def effect_label(d):
    d = abs(d)
    if d < 0.147: return "negligible"
    if d < 0.330: return "small"
    if d < 0.474: return "medium"
    return "large"


# ── Piecewise breakpoint detection ───────────────────────────────────────────

def piecewise_breakpoint(rho, residual_pct, label=""):
    """
    Scan all candidate breakpoints (interior ρ values).
    For each breakpoint ρ*, fit a constant (mean) to the left segment
    and a linear model to the right segment.
    Choose ρ* minimising total SSR.
    Returns: breakpoint_rho, ssr_left, ssr_right, improvement_pct
    """
    rho  = np.asarray(rho, float)
    res  = np.asarray(residual_pct, float)
    n    = len(rho)
    order = np.argsort(rho)
    rho, res = rho[order], res[order]

    best_ssr = np.inf
    best_bp  = None
    baseline_ssr = np.sum((res - np.mean(res))**2)

    # Need at least 2 points on each side
    for i in range(1, n-1):
        bp = rho[i]
        left  = res[:i+1]
        right = res[i+1:]

        ssr_l = np.sum((left - np.mean(left))**2) if len(left) > 1 else 0.0

        # Linear fit on right segment
        rho_r = rho[i+1:]
        if len(rho_r) >= 2:
            slope, intercept, *_ = stats.linregress(rho_r, right)
            fit_r = slope * rho_r + intercept
            ssr_r = np.sum((right - fit_r)**2)
        else:
            ssr_r = 0.0

        total = ssr_l + ssr_r
        if total < best_ssr:
            best_ssr = total
            best_bp  = bp

    improvement = (baseline_ssr - best_ssr) / baseline_ssr * 100 if baseline_ssr > 0 else 0
    if label:
        print(f"  [{label}]  breakpoint ρ* ≈ {best_bp:.2f}  "
              f"SSR {baseline_ssr:.1f} → {best_ssr:.1f}  "
              f"improvement = {improvement:.1f}%")
    return best_bp, improvement


# ── Coefficient of variation per group ───────────────────────────────────────

def replicate_stats(raw: pd.DataFrame, groupby_cols, value_col):
    """Mean, std, CV%, 95% CI per group (from raw replicates)."""
    def agg(g):
        m   = g[value_col].mean()
        s   = g[value_col].std(ddof=1)
        n   = len(g)
        ci  = stats.t.ppf(0.975, df=n-1) * s / np.sqrt(n) if n > 1 else np.nan
        cv  = s / m * 100 if m != 0 else np.nan
        return pd.Series({'n': n, 'mean': m, 'std': s, 'ci95': ci, 'cv_pct': cv})
    return raw.groupby(groupby_cols).apply(agg).reset_index()


# ── Main ─────────────────────────────────────────────────────────────────────

def run():
    D        = load_all()
    arr_raw  = D['arr_raw']
    arr_val  = D['arr_val']

    stable_raw = arr_raw[arr_raw['Rho_Target'] < 1.0].copy()

    # ── 1. Replicate statistics ───────────────────────────────────────────────
    print("\n=== REPLICATE STATISTICS (latency, CV%) ===")
    cv_df = replicate_stats(stable_raw, ['Mode', 'Rho_Target'], 'Mean_Latency_ms')
    print(cv_df.to_string(index=False))
    cv_df.round(3).to_csv(OUTPUT_DIR / "replicate_stats.csv", index=False)

    # ── 2. Effect size VT vs PT ───────────────────────────────────────────────
    print("\n=== EFFECT SIZE: VT vs PT ===")
    effect_records = []
    rho_levels = sorted(stable_raw['Rho_Target'].unique())
    for rho in rho_levels:
        vt_lat = stable_raw[(stable_raw['Mode']=='VT') & (stable_raw['Rho_Target']==rho)]['Mean_Latency_ms'].values
        pt_lat = stable_raw[(stable_raw['Mode']=='PT') & (stable_raw['Rho_Target']==rho)]['Mean_Latency_ms'].values
        d   = cohens_d(vt_lat, pt_lat)
        clf = cliffs_delta(vt_lat, pt_lat)
        mv  = np.mean(vt_lat) if len(vt_lat) else np.nan
        mp  = np.mean(pt_lat) if len(pt_lat) else np.nan
        lbl = effect_label(clf)
        print(f"  ρ={rho:.2f}: VT mean={mv:.1f} ms, PT mean={mp:.1f} ms | "
              f"Cohen's d={d:.3f}, Cliff's δ={clf:.3f} ({lbl})")
        effect_records.append(dict(rho=rho,
            vt_mean=mv, pt_mean=mp,
            cohens_d=round(d, 4), cliffs_delta=round(clf, 4), label=lbl))

    df_effect = pd.DataFrame(effect_records)
    df_effect.to_csv(OUTPUT_DIR / "effect_size.csv", index=False)

    # Also across all rho levels (pooled VT vs PT)
    vt_all = stable_raw[stable_raw['Mode']=='VT']['Mean_Latency_ms'].values
    pt_all = stable_raw[stable_raw['Mode']=='PT']['Mean_Latency_ms'].values
    d_all  = cohens_d(vt_all, pt_all)
    clf_all = cliffs_delta(vt_all, pt_all)
    print(f"\n  [Pooled VT vs PT]  Cohen's d={d_all:.3f}  Cliff's δ={clf_all:.3f} ({effect_label(clf_all)})")

    # Mann-Whitney U test (pooled)
    U, p_mwu = stats.mannwhitneyu(vt_all, pt_all, alternative='two-sided')
    print(f"  Mann-Whitney U={U:.0f}, p={p_mwu:.4f}")

    # ── 3. Breakpoint detection on residuals ──────────────────────────────────
    print("\n=== PIECEWISE BREAKPOINT DETECTION ===")
    av_s = arr_val[arr_val['Rho_Target'] < 1.0]
    vt_v = av_s[av_s['Mode']=='VT']
    pt_v = av_s[av_s['Mode']=='PT']

    bp_records = []
    for mode_label, subset in [("VT", vt_v), ("PT", pt_v)]:
        rho_arr = subset['Rho_Target'].values
        res_arr = subset['Latency_Error_Pct'].values   # already parsed as float
        bp, improvement = piecewise_breakpoint(rho_arr, res_arr, label=mode_label)
        bp_records.append(dict(mode=mode_label, breakpoint_rho=bp,
                               ssr_improvement_pct=round(improvement, 2)))

    df_bp = pd.DataFrame(bp_records)
    print("\n  Breakpoint summary:")
    print(df_bp.to_string(index=False))
    df_bp.to_csv(OUTPUT_DIR / "breakpoint_detection.csv", index=False)

    # ── 4. Correlation matrix ─────────────────────────────────────────────────
    print("\n=== CORRELATION MATRIX (arrival sweep raw, stable) ===")
    corr_cols = ['Lambda_Achieved_rps', 'Mean_Latency_ms', 'P99_ms',
                 'Nq_Hikari_Mean', 'Heap_Mean_MB', 'Threads_Mean', 'Rho_Target']
    available = [c for c in corr_cols if c in stable_raw.columns]

    for mode in ['VT', 'PT']:
        sub = stable_raw[stable_raw['Mode']==mode][available]
        corr = sub.corr(method='spearman')
        print(f"\n  [Spearman, Mode={mode}]")
        print(corr.round(3).to_string())
        corr.round(3).to_csv(OUTPUT_DIR / f"correlation_{mode}.csv")

    # ── LaTeX table: effect size ───────────────────────────────────────────────
    _write_table4_latex(df_effect, d_all, clf_all, p_mwu)

    print(f"\n✓ Saved outputs to {OUTPUT_DIR}/")
    return df_effect, df_bp


def _write_table4_latex(df_effect, d_all, clf_all, p_mwu):
    lines = [
        r"\begin{table}[ht]",
        r"\centering",
        r"\caption{Effect size comparing Virtual Thread (VT) to Platform Thread (PT) "
        r"mean latency at each utilisation level $\rho$. "
        r"Cohen's $d$: pooled-variance standardised mean difference. "
        r"Cliff's $\delta$: non-parametric dominance statistic "
        r"(|$\delta$| $<0.147$ negligible, $<0.33$ small, $<0.474$ medium, else large). "
        r"Pooled Mann--Whitney $p$ "
        rf"= {p_mwu:.4f}.}}",
        r"\label{tab:effect_size}",
        r"\begin{tabular}{rrrrrr}",
        r"\toprule",
        r"$\rho$ & VT mean (ms) & PT mean (ms) & Cohen's $d$ & Cliff's $\delta$ & Magnitude \\",
        r"\midrule",
    ]
    for _, row in df_effect.iterrows():
        lines.append(
            f"  {row['rho']:.2f} & {row['vt_mean']:.1f} & {row['pt_mean']:.1f}"
            f" & {row['cohens_d']:.3f} & {row['cliffs_delta']:.3f} & {row['label']} \\\\"
        )
    lines += [
        rf"\midrule",
        rf"\multicolumn{{6}}{{l}}{{\footnotesize Pooled (all $\rho$): "
        rf"Cohen's $d = {d_all:.3f}$, Cliff's $\delta = {clf_all:.3f}$ ({effect_label(clf_all)})"
        rf"}} \\",
        r"\bottomrule",
        r"\end{tabular}",
        r"\end{table}",
    ]
    path = OUTPUT_DIR / "table4_effect_size.tex"
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"✓ Saved: {path}")


if __name__ == "__main__":
    run()
