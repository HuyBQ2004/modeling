"""
04_figures.py
=============
Publication-quality figures for the paper.

Figure inventory:
  fig1_operating_envelope.pdf   – Region bands on the ρ axis
  fig2_latency_vs_rho.pdf       – Measured vs model latency as function of ρ (VT & PT)
  fig3_calibration.pdf          – Calibration plots (measured vs predicted), y=x line
  fig4_residual.pdf             – Signed relative error vs ρ  + piecewise breakpoint
  fig5_bland_altman.pdf         – Bland-Altman agreement plot (latency)
  fig6_vt_vs_pt.pdf             – VT vs PT latency comparison (mean ± CI, raw reps)
  fig7_correlation_heatmap.pdf  – Spearman correlation heatmap from raw arrival data
  fig8_resource_reallocation.pdf – THE thesis figure: blocking changes resource currency

All figures saved as PDF (vector, publication) + PNG (300 dpi, preview).

Run standalone:
    python 04_figures.py
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

import numpy as np
import pandas as pd
from scipy import stats
import matplotlib
matplotlib.use("Agg")          # headless rendering
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import matplotlib.ticker as mticker
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


# ── Global style ──────────────────────────────────────────────────────────────
plt.rcParams.update({
    "font.family":        "serif",
    "font.size":          10,
    "axes.labelsize":     11,
    "axes.titlesize":     12,
    "xtick.labelsize":    9,
    "ytick.labelsize":    9,
    "legend.fontsize":    9,
    "legend.framealpha":  0.85,
    "figure.dpi":         150,
    "savefig.dpi":        300,
    "savefig.bbox":       "tight",
    "axes.spines.top":    False,
    "axes.spines.right":  False,
    "axes.grid":          True,
    "grid.alpha":         0.3,
    "grid.linewidth":     0.5,
})

# Colour palette
C = {
    "VT":     "#1f77b4",   # steel blue
    "PT":     "#d62728",   # brick red
    "model":  "#2ca02c",   # forest green
    "region": {
        "Queue-free": "#d0e8ff",
        "Queueing":   "#fff3cd",
        "Saturated":  "#ffd6d6",
        "Unstable":   "#e0e0e0",
    },
}


def _save(fig, name):
    """Save figure as PDF and PNG."""
    for ext in ("pdf", "png"):
        path = OUTPUT_DIR / f"{name}.{ext}"
        fig.savefig(path)
    print(f"  ✓  {name}.pdf  +  {name}.png")
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Fig 1 · Operating Envelope
# ─────────────────────────────────────────────────────────────────────────────

def fig1_operating_envelope(boundaries: pd.DataFrame):
    """
    Horizontal band diagram showing the 4 operating regions on the ρ axis.
    Boundaries derived from Erlang-C model + empirical data.
    """
    # Empirical boundaries from data
    rho_qf_end   = 0.769   # P(wait) = 0.05
    rho_q_end    = 0.900   # ρ = 0.90
    rho_sat_end  = 0.983   # E[Wq] = 1/μ
    rho_max      = 1.10

    # (label, lo, hi, color, fontsize) — narrow bands get smaller text.
    # Labels state what each region means for the THESIS (resource-currency
    # transformation), not just the queueing regime.
    regions = [
        ("Region I\nAnalytical model valid",          0.0,        rho_qf_end,  C["region"]["Queue-free"], 8.5),
        ("Region II\nResource\ntransformation\nbegins", rho_qf_end, rho_q_end,  C["region"]["Queueing"],   7),
        ("Region III\nContinuation\naccumulation",    rho_q_end,   rho_sat_end, C["region"]["Saturated"], 6.5),
        ("Region IV\nModel\nbreakdown",               rho_sat_end, rho_max,     C["region"]["Unstable"],  7),
    ]

    fig, ax = plt.subplots(figsize=(10, 2.6))

    for (label, lo, hi, color, fs) in regions:
        ax.barh(0, hi - lo, left=lo, height=0.55, color=color,
                edgecolor="#555", linewidth=0.7)
        cx = (lo + hi) / 2
        ax.text(cx, 0, label, ha="center", va="center",
                fontsize=fs, fontweight="bold", color="#333")

    # Boundary lines + labels (staggered heights so adjacent labels don't collide)
    for rho, lbl, ytxt in [(rho_qf_end,  f"ρ* = {rho_qf_end}", 0.34),
                           (rho_q_end,   f"ρ = {rho_q_end}",   0.34),
                           (rho_sat_end, f"ρ = {rho_sat_end}", 0.56)]:
        ax.axvline(rho, color="#444", linewidth=1.2, linestyle="--")
        ax.text(rho, ytxt, lbl, ha="center", va="bottom",
                fontsize=7.5, color="#444", rotation=0,
                bbox=dict(boxstyle="round,pad=0.2", fc="white", ec="none", alpha=0.8))

    # Data points (measured rho levels) — full 10-level grid used in this
    # revision (§4.2), not the original 6-level pilot grid. The four
    # intermediate levels {0.60, 0.65, 0.75, 0.80} sit close together, so
    # labels are staggered onto two rows to avoid overlapping text.
    measured_rhos = [0.30, 0.50, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.95, 1.05]
    ax.scatter(measured_rhos, [-0.32]*len(measured_rhos),
               color="#333", s=32, zorder=5, clip_on=False)
    for i, r in enumerate(measured_rhos):
        ytxt = -0.52 if i % 2 == 0 else -0.74
        ax.plot([r, r], [-0.36, ytxt + 0.03], color="#bbb", linewidth=0.6,
                 zorder=1, clip_on=False)
        ax.text(r, ytxt, f"{r:g}", ha="center", va="top", fontsize=7, color="#333")
    ax.text(-0.02, -0.52, "Measured ρ:", ha="right", va="top",
            fontsize=7.5, color="#333", transform=ax.transData)

    ax.set_xlim(0.0, rho_max)
    ax.set_ylim(-0.90, 0.85)
    ax.set_xlabel("Server utilisation  ρ = λ / (c · μ)", fontsize=11)
    ax.set_title("Operating Envelope of the Erlang-C Analytical Model", fontsize=12, pad=8)
    ax.yaxis.set_visible(False)
    ax.spines["left"].set_visible(False)
    ax.spines["bottom"].set_visible(True)
    ax.grid(False)

    fig.tight_layout()
    _save(fig, "fig1_operating_envelope")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 2 · Latency vs ρ  (measured + model, VT & PT)
# ─────────────────────────────────────────────────────────────────────────────

def _replicate_ci95(arr_raw: pd.DataFrame, mode: str, value_col: str = 'Mean_Latency_ms'):
    """
    Proper 95% CI from raw per-replicate values: Student-t critical value
    (df = n-1) times the *sample* (Bessel-corrected, ddof=1) standard error.
    Used instead of the benchmark's own CI95_* CSV columns, which are computed
    in the Java harness with a fixed z=1.96 multiplier and population
    (ddof=0) SD — an asymptotic approximation that understates the interval
    at n=20 by roughly 9-13% relative to the Student-t/sample-SD value used
    here and throughout the paper text (see §4.4).
    """
    sub = arr_raw[(arr_raw['Mode'] == mode) & (arr_raw['Rho_Target'] < 1.0)]
    rows = []
    for rho, grp in sub.groupby('Rho_Target'):
        vals = grp[value_col].dropna().values
        n = len(vals)
        mean = vals.mean()
        if n > 1:
            s = vals.std(ddof=1)
            ci = stats.t.ppf(0.975, df=n - 1) * s / np.sqrt(n)
        else:
            ci = 0.0
        rows.append({'Rho_Target': rho, 'Measured_Mean_Latency_ms': mean, 'CI95_Latency_ms': ci})
    return pd.DataFrame(rows).sort_values('Rho_Target')


def fig2_latency_vs_rho(arr_val: pd.DataFrame, arr_sum: pd.DataFrame, arr_raw: pd.DataFrame = None):
    stable_v = arr_val[arr_val['Rho_Target'] < 1.0]
    stable_s = arr_sum[arr_sum['Rho_Target'] < 1.0]

    vt_v = stable_v[stable_v['Mode']=='VT'].sort_values('Rho_Target')
    pt_v = stable_v[stable_v['Mode']=='PT'].sort_values('Rho_Target')
    vt_s = stable_s[stable_s['Mode']=='VT'].sort_values('Rho_Target')
    pt_s = stable_s[stable_s['Mode']=='PT'].sort_values('Rho_Target')

    # Recompute measured mean + CI95 from raw replicates (Student-t, sample SD)
    # rather than trusting the CI95_Latency_ms column in arr_val (see docstring
    # of _replicate_ci95 above). Falls back to the CSV column if raw data is
    # unavailable, so this function still runs standalone.
    if arr_raw is not None:
        vt_ci = _replicate_ci95(arr_raw, 'VT')
        pt_ci = _replicate_ci95(arr_raw, 'PT')
    else:
        vt_ci = vt_v[['Rho_Target', 'Measured_Mean_Latency_ms', 'CI95_Latency_ms']]
        pt_ci = pt_v[['Rho_Target', 'Measured_Mean_Latency_ms', 'CI95_Latency_ms']]

    fig, ax = plt.subplots(figsize=(7, 4.5))

    # Shaded regions (subtle)
    ax.axvspan(0,     0.769, alpha=0.06, color=C["region"]["Queue-free"], label=None)
    ax.axvspan(0.769, 0.900, alpha=0.10, color=C["region"]["Queueing"],   label=None)
    ax.axvspan(0.900, 0.983, alpha=0.10, color=C["region"]["Saturated"],  label=None)
    ax.axvspan(0.983, 1.05,  alpha=0.08, color=C["region"]["Unstable"],   label=None)

    rho_model = vt_v['Rho_Target'].values
    pred_lat  = vt_v['Model_Mean_Latency_ms'].values

    # Model prediction (same for VT & PT — same Erlang-C)
    ax.plot(rho_model, pred_lat, color=C["model"], linewidth=1.8, linestyle="--",
            marker="D", markersize=5, zorder=4, label="Model (Erlang-C)")

    # The PT rho=0.75 cell is a ~10x measurement anomaly (mean 753ms, 95% CI
    # +-1417ms; see paper Sec 5.2) that, if plotted at full scale, stretches
    # the y-axis so every other point collapses to a flat line. We plot the
    # PT trajectory *excluding* that one cell and call it out separately with
    # an explicit off-scale annotation, rather than let it dominate the chart.
    ANOMALY_RHO = 0.75
    pt_ci_plot = pt_ci[pt_ci['Rho_Target'] != ANOMALY_RHO]
    pt_anomaly = pt_ci[pt_ci['Rho_Target'] == ANOMALY_RHO]

    # VT measured (Student-t / sample-SD CI, recomputed from raw replicates)
    ax.errorbar(vt_ci['Rho_Target'], vt_ci['Measured_Mean_Latency_ms'],
                yerr=vt_ci['CI95_Latency_ms'],
                color=C["VT"], linewidth=1.6, marker="o", markersize=6,
                capsize=4, capthick=1.2, label="VT measured (95% CI)", zorder=5)

    # PT measured (anomalous cell excluded from the line — see above)
    ax.errorbar(pt_ci_plot['Rho_Target'], pt_ci_plot['Measured_Mean_Latency_ms'],
                yerr=pt_ci_plot['CI95_Latency_ms'],
                color=C["PT"], linewidth=1.6, marker="s", markersize=6,
                capsize=4, capthick=1.2, label="PT measured (95% CI)", zorder=5)

    # Compute y-limits from the plotted (non-anomalous) data only, then draw
    # the anomaly as a clipped, clearly-labelled off-scale marker.
    normal_upper = pd.concat([
        vt_ci['Measured_Mean_Latency_ms'] + vt_ci['CI95_Latency_ms'],
        pt_ci_plot['Measured_Mean_Latency_ms'] + pt_ci_plot['CI95_Latency_ms'],
        pd.Series(pred_lat),
    ])
    normal_lower = pd.concat([
        vt_ci['Measured_Mean_Latency_ms'] - vt_ci['CI95_Latency_ms'],
        pt_ci_plot['Measured_Mean_Latency_ms'] - pt_ci_plot['CI95_Latency_ms'],
        pd.Series(pred_lat),
    ])
    ymax = float(normal_upper.max())
    ymin = min(0.0, float(normal_lower.min()))
    pad  = 0.12 * (ymax - ymin)
    ax.set_ylim(ymin - 0.02 * (ymax - ymin), ymax + pad)

    if len(pt_anomaly):
        anomaly_val = float(pt_anomaly['Measured_Mean_Latency_ms'].iloc[0])
        anomaly_ci  = float(pt_anomaly['CI95_Latency_ms'].iloc[0])
        y_top = ymax + pad
        ax.annotate(
            f"PT ρ={ANOMALY_RHO:g}: {anomaly_val:.0f} ms\n"
            f"(95% CI ±{anomaly_ci:.0f} ms)\noff-scale — see §5.2",
            xy=(ANOMALY_RHO, y_top * 0.97), xytext=(ANOMALY_RHO, y_top * 0.97),
            ha="center", va="top", fontsize=7, color=C["PT"], style="italic",
            bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=C["PT"], alpha=0.9),
            zorder=6,
        )
        ax.scatter([ANOMALY_RHO], [y_top * 0.80], marker="^", s=55,
                   color=C["PT"], zorder=6, clip_on=True)

    # Thesis annotation: the VT–model gap at the highest stable ρ is the
    # continuation-induced waiting that Erlang-C does not capture. Placed in
    # the upper-right, well clear of both the data and the off-scale callout.
    rho_hi   = vt_v['Rho_Target'].values[-1]
    vt_hi    = vt_v['Measured_Mean_Latency_ms'].values[-1]
    model_hi = pred_lat[-1]
    ax.annotate("", xy=(rho_hi - 0.012, vt_hi), xytext=(rho_hi - 0.012, model_hi),
                arrowprops=dict(arrowstyle="<->", color="#555", linewidth=1.1))
    ax.annotate("continuation-induced\nwaiting (unmodeled)",
                xy=(rho_hi - 0.02, (vt_hi + model_hi) / 2),
                xytext=(0.90, ymax + pad * 0.55),
                ha="center", va="center", fontsize=7.5, color="#555",
                style="italic",
                arrowprops=dict(arrowstyle="-", color="#999", linewidth=0.8))

    # Boundary annotation (x in data coords, y in axes fraction — always inside plot)
    import matplotlib.transforms as mtransforms
    trans = mtransforms.blended_transform_factory(ax.transData, ax.transAxes)
    ax.axvline(0.769, color="#888", linewidth=0.9, linestyle=":")
    ax.text(0.769+0.005, 0.02, "ρ*=0.77", fontsize=7.5, color="#666",
            ha="left", va="bottom", transform=trans)

    ax.set_xlabel("Server utilisation  ρ", fontsize=11)
    ax.set_ylabel("Mean response time (ms)", fontsize=11)
    ax.set_title("Mean Latency: Model Predictions vs Measurements (VT & PT)", fontsize=12)
    ax.legend(loc="upper left", frameon=True)
    ax.set_xlim(0.25, 1.0)

    fig.tight_layout()
    _save(fig, "fig2_latency_vs_rho")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 3 · Calibration plots  (predicted vs measured, y=x)
# ─────────────────────────────────────────────────────────────────────────────

def _calibration_panel(ax, pred_vt, meas_vt, pred_pt, meas_pt,
                        rho_vt, rho_pt, xlabel, ylabel, title, unit,
                        anomaly_rho=0.75):
    """
    Draw one calibration panel with y=x line, R², MAPE.

    MAPE/R2/r are computed over ALL points (including the PT anomaly_rho
    cell) so the annotated stats match Table 1 exactly. The axis *limits*,
    however, are computed excluding that one cell, so the other eight points
    are not all squashed into a corner by a single ~10x outlier; the excluded
    point is instead called out with an explicit off-scale arrow.
    """
    all_pred = np.concatenate([pred_vt, pred_pt])
    all_meas = np.concatenate([meas_vt, meas_pt])
    all_rho  = np.concatenate([rho_vt, rho_pt])
    ok = np.isfinite(all_pred) & np.isfinite(all_meas)

    # Points used for axis-limit calculation: finite, and not the anomaly cell
    zoom_mask = ok & ~np.isclose(all_rho, anomaly_rho)

    # y=x reference, scaled to the zoomed (non-anomaly) range
    lo = min(all_pred[zoom_mask].min(), all_meas[zoom_mask].min()) * 0.85
    hi = max(all_pred[zoom_mask].max(), all_meas[zoom_mask].max()) * 1.15
    ax.plot([lo, hi], [lo, hi], color="#aaa", linewidth=1.2,
            linestyle="--", zorder=1, label="y = x (perfect)")

    # Scatter colored by rho (points outside [lo,hi] are simply clipped by
    # the axes — the anomaly is called out separately below instead)
    cmap = plt.cm.plasma
    norm = plt.Normalize(all_rho[ok].min(), all_rho[ok].max())

    sc_vt = ax.scatter(pred_vt, meas_vt, c=rho_vt, cmap=cmap, norm=norm,
                        marker="o", s=70, zorder=5, edgecolors=C["VT"],
                        linewidths=1.0, label="VT")
    ax.scatter(pred_pt, meas_pt, c=rho_pt, cmap=cmap, norm=norm,
               marker="s", s=70, zorder=5, edgecolors=C["PT"],
               linewidths=1.0, label="PT")

    # ±30% bands
    ax.fill_between([lo, hi], [lo*0.7, hi*0.7], [lo*1.3, hi*1.3],
                    alpha=0.07, color="#888", label="±30% band")

    # Stats annotation — computed over ALL points, matching Table 1
    pred_ok, meas_ok = all_pred[ok], all_meas[ok]
    mae  = np.mean(np.abs(meas_ok - pred_ok))
    mape = np.mean(np.abs((meas_ok - pred_ok) / pred_ok)) * 100 if pred_ok.any() else np.nan
    ss_res = np.sum((meas_ok - pred_ok)**2)
    ss_tot = np.sum((meas_ok - np.mean(meas_ok))**2)
    r2 = 1 - ss_res/ss_tot if ss_tot > 0 else np.nan
    r,  _ = stats.pearsonr(pred_ok, meas_ok) if len(pred_ok) >= 3 else (np.nan, np.nan)

    # Top-right corner: stats box, labelled as including the off-scale point.
    # (Top-left is avoided: with x compressed to the narrow predicted-value
    # range and y stretched to include off-diagonal points, poorly-calibrated
    # points systematically land top-left and collide with the box there.
    # Top-right, beyond the max predicted value, is always empty.)
    ax.text(0.96, 0.96,
            f"MAPE = {mape:.1f}%\n$R^2$ = {r2:.3f}\n$r$ = {r:.3f}\n"
            f"(incl. 1 off-scale pt.)",
            transform=ax.transAxes, va="top", ha="right", fontsize=8,
            bbox=dict(boxstyle="round,pad=0.35", fc="white", ec="#ccc", alpha=0.95))

    # Off-scale annotation for the excluded anomaly point, if present
    anomaly_mask_pt = np.isclose(rho_pt, anomaly_rho)
    if anomaly_mask_pt.any():
        av = float(pred_pt[anomaly_mask_pt][0]), float(meas_pt[anomaly_mask_pt][0])
        ax.annotate(
            f"PT ρ={anomaly_rho:g}\n({av[0]:.0f}, {av[1]:.0f}) {unit}\noff-scale",
            xy=(0.97, 0.04), xycoords="axes fraction",
            ha="right", va="bottom", fontsize=7, color=C["PT"], style="italic",
            bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=C["PT"], alpha=0.9),
        )

    ax.set_xlabel(f"Model predicted {xlabel} ({unit})", fontsize=10)
    ax.set_ylabel(f"Measured {ylabel} ({unit})", fontsize=10)
    ax.set_title(title, fontsize=10, pad=6)
    ax.set_xlim(lo, hi); ax.set_ylim(lo, hi)
    ax.set_aspect("equal", adjustable="box")
    return sc_vt


def fig3_calibration(arr_val: pd.DataFrame):
    stable = arr_val[arr_val['Rho_Target'] < 1.0]
    vt = stable[stable['Mode']=='VT']
    pt = stable[stable['Mode']=='PT']

    fig, axes = plt.subplots(1, 2, figsize=(11, 5))
    fig.suptitle("Model Calibration: Predicted vs Measured (Stable Regime, ρ < 1)",
                 fontsize=13, y=1.01)

    # ── Panel A: Mean Latency ──────────────────────────────────────────────────
    sc = _calibration_panel(
        axes[0],
        vt['Model_Mean_Latency_ms'].values, vt['Measured_Mean_Latency_ms'].values,
        pt['Model_Mean_Latency_ms'].values, pt['Measured_Mean_Latency_ms'].values,
        vt['Rho_Target'].values, pt['Rho_Target'].values,
        xlabel="mean latency", ylabel="mean latency",
        title="(a) Mean Response Time", unit="ms"
    )

    # ── Panel B: Queue Length ──────────────────────────────────────────────────
    _calibration_panel(
        axes[1],
        vt['Model_E_Nq'].values, vt['Measured_Nq'].values,
        pt['Model_E_Nq'].values, pt['Measured_Nq'].values,
        vt['Rho_Target'].values, pt['Rho_Target'].values,
        xlabel="queue length E[Nq]", ylabel="queue length",
        title="(b) Queue Length", unit="req"
    )

    # Shared colourbar for ρ
    cmap = plt.cm.plasma
    rho_all = stable['Rho_Target'].values
    norm = plt.Normalize(rho_all.min(), rho_all.max())
    sm = plt.cm.ScalarMappable(cmap=cmap, norm=norm)
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=axes, orientation="vertical",
                        pad=0.02, shrink=0.80, label="ρ (utilisation)")

    # Legend for markers
    h_vt = mpatches.Patch(facecolor="white", edgecolor=C["VT"], linewidth=1.5, label="VT  ○")
    h_pt = mpatches.Patch(facecolor="white", edgecolor=C["PT"], linewidth=1.5, label="PT  □")
    h_yx = plt.Line2D([0],[0], color="#aaa", linestyle="--", label="y = x (ideal)")
    fig.legend(handles=[h_vt, h_pt, h_yx], loc="lower center",
               ncol=3, bbox_to_anchor=(0.45, -0.07), frameon=True)

    _save(fig, "fig3_calibration")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 4 · Residual vs ρ  +  piecewise breakpoint
# ─────────────────────────────────────────────────────────────────────────────

def _find_breakpoint(rho, res):
    """Simple piecewise breakpoint scan (minimise total SSR)."""
    rho, res = np.asarray(rho), np.asarray(res)
    order = np.argsort(rho)
    rho, res = rho[order], res[order]
    n = len(rho)
    best_ssr, best_bp = np.inf, None
    for i in range(1, n-1):
        left  = res[:i+1]
        right = res[i+1:]
        ssr_l = np.sum((left - np.mean(left))**2)
        if len(right) >= 2:
            m,b,*_ = stats.linregress(rho[i+1:], right)
            ssr_r  = np.sum((right - (m*rho[i+1:]+b))**2)
        else:
            ssr_r = 0.0
        total = ssr_l + ssr_r
        if total < best_ssr:
            best_ssr = total; best_bp = rho[i]
    return best_bp


def _bootstrap_breakpoint(arr_raw, arr_val, mode, n_boot=2000, seed=42):
    """
    Bootstrap 95% CI for the breakpoint ρ*: resample replicates within each ρ
    level, recompute mean relative residuals, rerun the breakpoint scan.
    """
    rng = np.random.default_rng(seed)
    raw = arr_raw[(arr_raw['Mode'] == mode) & (arr_raw['Rho_Target'] < 1.0)]
    val = arr_val[(arr_val['Mode'] == mode) & (arr_val['Rho_Target'] < 1.0)]
    model = val.set_index('Rho_Target')['Model_Mean_Latency_ms']

    groups = {rho: grp['Mean_Latency_ms'].values
              for rho, grp in raw.groupby('Rho_Target') if rho in model.index}
    rhos = np.array(sorted(groups))

    bps = []
    for _ in range(n_boot):
        res = []
        for rho in rhos:
            lat = groups[rho]
            samp = rng.choice(lat, size=len(lat), replace=True)
            res.append((samp.mean() - model[rho]) / model[rho] * 100)
        bp = _find_breakpoint(rhos, np.array(res))
        if bp is not None:
            bps.append(bp)
    bps = np.array(bps)
    return np.percentile(bps, 2.5), np.percentile(bps, 97.5)


def fig4_residual(arr_val: pd.DataFrame, arr_raw: pd.DataFrame = None):
    stable = arr_val[arr_val['Rho_Target'] < 1.0].copy()
    stable['Residual_Pct'] = stable['Latency_Error_Pct']   # already computed

    vt = stable[stable['Mode']=='VT'].sort_values('Rho_Target')
    pt = stable[stable['Mode']=='PT'].sort_values('Rho_Target')

    bp_vt = _find_breakpoint(vt['Rho_Target'].values, vt['Residual_Pct'].values)
    bp_pt = _find_breakpoint(pt['Rho_Target'].values, pt['Residual_Pct'].values)

    # Bootstrap CIs (reviewer request): resample reps → distribution of ρ*
    ci_vt = ci_pt = None
    if arr_raw is not None:
        ci_vt = _bootstrap_breakpoint(arr_raw, arr_val, "VT")
        ci_pt = _bootstrap_breakpoint(arr_raw, arr_val, "PT")

    fig, ax = plt.subplots(figsize=(7, 4.5))

    # Shaded bootstrap CI bands for ρ*
    for ci, mode_c in [(ci_vt, C["VT"]), (ci_pt, C["PT"])]:
        if ci is not None and ci[1] > ci[0]:
            ax.axvspan(ci[0], ci[1], alpha=0.10, color=mode_c, zorder=1)

    # Shaded acceptable zone
    ax.axhspan(-30, 30, alpha=0.06, color="#2ca02c", label="±30% accuracy band")
    ax.axhline(0, color="#999", linewidth=0.8, linestyle="-")

    ax.plot(vt['Rho_Target'], vt['Residual_Pct'], color=C["VT"],
            marker="o", markersize=7, linewidth=1.6, label="VT", zorder=4)
    ax.plot(pt['Rho_Target'], pt['Residual_Pct'], color=C["PT"],
            marker="s", markersize=7, linewidth=1.6, label="PT", zorder=4)

    # Breakpoint markers (with bootstrap 95% CI in the label if available)
    for bp, ci, mode_c, mode_lbl in [(bp_vt, ci_vt, C["VT"], "VT"),
                                      (bp_pt, ci_pt, C["PT"], "PT")]:
        if bp is not None:
            lbl = f"Breakpoint {mode_lbl} ρ* ≈ {bp:.2f}"
            if ci is not None:
                lbl += f"  (95% CI [{ci[0]:.2f}, {ci[1]:.2f}])"
            ax.axvline(bp, color=mode_c, linewidth=1.2, linestyle=":",
                       alpha=0.75, label=lbl)

    # Region annotations at top
    ax.axvspan(0,     0.769, alpha=0.05, color=C["region"]["Queue-free"])
    ax.axvspan(0.769, 0.900, alpha=0.08, color=C["region"]["Queueing"])
    ax.axvspan(0.900, 1.00,  alpha=0.08, color=C["region"]["Saturated"])

    ax.set_xlabel("Server utilisation  ρ", fontsize=11)
    ax.set_ylabel("Relative prediction error  (measured − predicted) / predicted  (%)",
                  fontsize=10)
    ax.set_title("Residual Analysis: Model Error as a Function of Utilisation", fontsize=12)
    ax.legend(loc="upper left", frameon=True, fontsize=8.5)
    ax.set_xlim(0.25, 1.0)

    # Threshold labels
    ax.text(0.99, 30, "+30%", ha="right", va="bottom", fontsize=7.5, color="#555")
    ax.text(0.99, -30, "−30%", ha="right", va="top", fontsize=7.5, color="#555")

    fig.tight_layout()
    _save(fig, "fig4_residual")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 5 · Bland–Altman  (latency prediction agreement)
# ─────────────────────────────────────────────────────────────────────────────

def fig5_bland_altman(arr_val: pd.DataFrame, anomaly_rho: float = 0.75, anomaly_mode: str = "PT"):
    stable = arr_val[arr_val['Rho_Target'] < 1.0].copy()
    stable['mean_val'] = (stable['Model_Mean_Latency_ms'] + stable['Measured_Mean_Latency_ms']) / 2
    stable['diff_val'] = stable['Measured_Mean_Latency_ms'] - stable['Model_Mean_Latency_ms']

    # Bias and limits of agreement (LoA) are computed over ALL points,
    # including the PT rho=0.75 anomaly, so they match the value quoted in
    # the paper text/§5.2. The plot *view* below is zoomed to the other 17
    # points so it is actually readable; the anomaly gets its own callout.
    bias  = stable['diff_val'].mean()
    sd    = stable['diff_val'].std(ddof=1)
    loa_u = bias + 1.96 * sd
    loa_l = bias - 1.96 * sd

    is_anomaly = (np.isclose(stable['Rho_Target'], anomaly_rho)) & (stable['Mode'] == anomaly_mode)
    zoomed = stable[~is_anomaly]
    anomaly = stable[is_anomaly]

    fig, ax = plt.subplots(figsize=(7, 4.5))

    # Shaded LoA band (spans the full bias/SD range, even though the visible
    # y-axis below is zoomed — the band's edges are simply clipped)
    ax.axhspan(loa_l, loa_u, alpha=0.08, color="#888", label="Limits of agreement")

    for mode, marker, color in [("VT", "o", C["VT"]), ("PT", "s", C["PT"])]:
        sub = zoomed[zoomed['Mode']==mode]
        sc = ax.scatter(sub['mean_val'], sub['diff_val'],
                        c=sub['Rho_Target'], cmap="plasma",
                        vmin=0.3, vmax=0.95,
                        marker=marker, s=80, zorder=5,
                        edgecolors=color, linewidths=1.2, label=mode)

    # Reference lines
    ax.axhline(bias,  color="#333", linewidth=1.4, linestyle="-",  label=f"Bias = {bias:.1f} ms")
    ax.axhline(loa_u, color="#888", linewidth=1.0, linestyle="--", label=f"+1.96 SD = {loa_u:.1f} ms")
    ax.axhline(loa_l, color="#888", linewidth=1.0, linestyle="--", label=f"−1.96 SD = {loa_l:.1f} ms")

    # Zoom the view to the non-anomaly points only. With 9 stable rho levels
    # per mode (18 points total, minus the 1 anomaly), individual per-point
    # rho labels collide badly at this density — rho is instead encoded
    # purely by marker colour (colourbar) rather than by text, avoiding the
    # overlapping-label problem entirely.
    pad_x = 0.12 * (zoomed['mean_val'].max() - zoomed['mean_val'].min() + 1e-9)
    pad_y = 0.18 * (zoomed['diff_val'].max() - zoomed['diff_val'].min() + 1e-9)
    ax.set_xlim(zoomed['mean_val'].min() - pad_x, zoomed['mean_val'].max() + pad_x)
    ax.set_ylim(zoomed['diff_val'].min() - pad_y, zoomed['diff_val'].max() + pad_y)

    if len(anomaly):
        row = anomaly.iloc[0]
        ax.annotate(
            f"{row['Mode']} ρ={row['Rho_Target']:g}: diff={row['diff_val']:.0f} ms\noff-scale (see §5.2)",
            xy=(0.97, 0.97), xycoords="axes fraction",
            ha="right", va="top", fontsize=7.5, color=C["PT"], style="italic",
            bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=C["PT"], alpha=0.9),
        )

    # Colourbar (sole encoding of rho — see note above)
    sm = plt.cm.ScalarMappable(cmap="plasma", norm=plt.Normalize(0.3, 0.95))
    sm.set_array([])
    fig.colorbar(sm, ax=ax, label="ρ (utilisation)", shrink=0.85)

    ax.set_xlabel("Mean of predicted and measured latency (ms)", fontsize=11)
    ax.set_ylabel("Measured − Predicted latency (ms)", fontsize=11)
    ax.set_title("Bland–Altman Agreement Plot: Latency (VT & PT)", fontsize=12)
    ax.legend(loc="upper left", frameon=True, fontsize=8)

    fig.tight_layout()
    _save(fig, "fig5_bland_altman")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 6 · VT vs PT comparison  (mean latency ± CI per ρ, from raw replicates)
# ─────────────────────────────────────────────────────────────────────────────

def fig6_vt_vs_pt(arr_raw: pd.DataFrame, arr_val: pd.DataFrame):
    stable_raw = arr_raw[arr_raw['Rho_Target'] < 1.0].copy()

    # Compute mean ± CI from raw replicates
    def group_stats(mode):
        g = stable_raw[stable_raw['Mode']==mode].groupby('Rho_Target')
        rhos, means, cis = [], [], []
        for rho, grp in g:
            m  = grp['Mean_Latency_ms'].mean()
            s  = grp['Mean_Latency_ms'].std(ddof=1)
            n  = len(grp)
            ci = stats.t.ppf(0.975, df=n-1) * s / np.sqrt(n) if n > 1 else 0
            rhos.append(rho); means.append(m); cis.append(ci)
        return np.array(rhos), np.array(means), np.array(cis)

    rho_vt, mean_vt, ci_vt = group_stats("VT")
    rho_pt, mean_pt, ci_pt = group_stats("PT")

    # Model prediction (same for both)
    av_vt = arr_val[(arr_val['Mode']=='VT') & (arr_val['Rho_Target'] < 1.0)].sort_values('Rho_Target')
    rho_m = av_vt['Rho_Target'].values
    pred  = av_vt['Model_Mean_Latency_ms'].values

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
    fig.suptitle("Virtual Threads vs Platform Threads: Latency and Queue Length", fontsize=13)

    # The PT rho=0.75 cell is a ~10x measurement anomaly (see fig2 / Sec 5.2).
    # Excluded from both panels' plotted lines and axis-limit computation;
    # called out explicitly instead so it doesn't compress the real trend.
    ANOMALY_RHO = 0.75

    def _exclude(rho_arr, mean_arr, ci_arr, anomaly=ANOMALY_RHO):
        mask = ~np.isclose(rho_arr, anomaly)
        return rho_arr[mask], mean_arr[mask], ci_arr[mask]

    def _anomaly_val(rho_arr, mean_arr, ci_arr, anomaly=ANOMALY_RHO):
        mask = np.isclose(rho_arr, anomaly)
        if not mask.any():
            return None
        return float(mean_arr[mask][0]), float(ci_arr[mask][0])

    def _clip_and_flag(ax, x_anomaly, *series, pad_frac=0.12, unit="ms", label="PT"):
        """series: list of (mean_array, ci_array_or_None) already excluding the anomaly."""
        highs, lows = [], []
        for mean_arr, ci_arr in series:
            ci_arr = ci_arr if ci_arr is not None else np.zeros_like(mean_arr)
            highs.append(np.max(mean_arr + ci_arr))
            lows.append(np.min(mean_arr - ci_arr))
        ymax = max(highs)
        ymin = min(0.0, min(lows))
        pad = pad_frac * (ymax - ymin)
        ax.set_ylim(ymin - 0.02 * (ymax - ymin), ymax + pad)
        return ymax, pad

    # ── Left panel: Latency ───────────────────────────────────────────────────
    offset = 0.008
    rho_pt_c, mean_pt_c, ci_pt_c = _exclude(rho_pt, mean_pt, ci_pt)
    ax1.errorbar(rho_vt - offset, mean_vt, yerr=ci_vt,
                 color=C["VT"], marker="o", markersize=7, linewidth=1.6,
                 capsize=5, capthick=1.2, label="VT measured ± 95% CI")
    ax1.errorbar(rho_pt_c + offset, mean_pt_c, yerr=ci_pt_c,
                 color=C["PT"], marker="s", markersize=7, linewidth=1.6,
                 capsize=5, capthick=1.2, label="PT measured ± 95% CI")
    ax1.plot(rho_m, pred, color=C["model"], linestyle="--", linewidth=1.6,
             marker="D", markersize=5, label="Model (Erlang-C)")

    ymax1, pad1 = _clip_and_flag(ax1, ANOMALY_RHO, (mean_vt, ci_vt), (mean_pt_c, ci_pt_c), (pred, None))
    anomaly1 = _anomaly_val(rho_pt, mean_pt, ci_pt)
    if anomaly1 is not None:
        val, ci = anomaly1
        # Marker sits just under the axis top at the anomaly's x-position;
        # the label text is offset to the side (not stacked directly above
        # the marker) and connected with a thin leader line, so the two
        # elements never overlap regardless of how little headroom `pad1`
        # leaves.
        marker_y1 = ymax1 + pad1 * 0.55
        ax1.scatter([ANOMALY_RHO], [marker_y1], marker="^", s=45, color=C["PT"], zorder=6)
        ax1.annotate(f"PT ρ={ANOMALY_RHO:g}: {val:.0f} ms\n(±{ci:.0f} ms) — off-scale",
                     xy=(ANOMALY_RHO, marker_y1), xycoords="data",
                     xytext=(8, 14), textcoords="offset points",
                     ha="left", va="bottom", fontsize=6.5, color=C["PT"], style="italic",
                     bbox=dict(boxstyle="round,pad=0.25", fc="white", ec=C["PT"], alpha=0.9),
                     arrowprops=dict(arrowstyle="-", color=C["PT"], linewidth=0.7))

    ax1.set_xlabel("Server utilisation  ρ", fontsize=11)
    ax1.set_ylabel("Mean response time (ms)", fontsize=11)
    ax1.set_title("(a) Mean Latency", fontsize=11)
    ax1.legend(frameon=True, fontsize=8.5, loc="upper left")

    # ── Right panel: Queue length ─────────────────────────────────────────────
    def queue_stats(mode):
        g = stable_raw[stable_raw['Mode']==mode].groupby('Rho_Target')
        rhos, means, cis = [], [], []
        for rho, grp in g:
            m  = grp['Nq_Hikari_Mean'].mean()
            s  = grp['Nq_Hikari_Mean'].std(ddof=1)
            n  = len(grp)
            ci = stats.t.ppf(0.975, df=n-1) * s / np.sqrt(n) if n > 1 else 0
            rhos.append(rho); means.append(m); cis.append(ci)
        return np.array(rhos), np.array(means), np.array(cis)

    rho_vt_q, mean_vt_q, ci_vt_q = queue_stats("VT")
    rho_pt_q, mean_pt_q, ci_pt_q = queue_stats("PT")

    # Model E[Nq]
    rho_m_q  = av_vt['Rho_Target'].values
    pred_nq  = av_vt['Model_E_Nq'].values

    rho_pt_q_c, mean_pt_q_c, ci_pt_q_c = _exclude(rho_pt_q, mean_pt_q, ci_pt_q)
    ax2.errorbar(rho_vt_q - offset, mean_vt_q, yerr=ci_vt_q,
                 color=C["VT"], marker="o", markersize=7, linewidth=1.6,
                 capsize=5, capthick=1.2, label="VT measured ± 95% CI")
    ax2.errorbar(rho_pt_q_c + offset, mean_pt_q_c, yerr=ci_pt_q_c,
                 color=C["PT"], marker="s", markersize=7, linewidth=1.6,
                 capsize=5, capthick=1.2, label="PT measured ± 95% CI")
    ax2.plot(rho_m_q, pred_nq, color=C["model"], linestyle="--", linewidth=1.6,
             marker="D", markersize=5, label="Model E[Nq]")

    ymax2, pad2 = _clip_and_flag(ax2, ANOMALY_RHO, (mean_vt_q, ci_vt_q), (mean_pt_q_c, ci_pt_q_c), (pred_nq, None))
    anomaly2 = _anomaly_val(rho_pt_q, mean_pt_q, ci_pt_q)
    if anomaly2 is not None:
        val, ci = anomaly2
        marker_y2 = ymax2 + pad2 * 0.55
        ax2.scatter([ANOMALY_RHO], [marker_y2], marker="^", s=45, color=C["PT"], zorder=6)
        ax2.annotate(f"PT ρ={ANOMALY_RHO:g}: {val:.0f} req\n(±{ci:.0f}) — off-scale",
                     xy=(ANOMALY_RHO, marker_y2), xycoords="data",
                     xytext=(8, 14), textcoords="offset points",
                     ha="left", va="bottom", fontsize=6.5, color=C["PT"], style="italic",
                     bbox=dict(boxstyle="round,pad=0.25", fc="white", ec=C["PT"], alpha=0.9),
                     arrowprops=dict(arrowstyle="-", color=C["PT"], linewidth=0.7))

    ax2.set_xlabel("Server utilisation  ρ", fontsize=11)
    ax2.set_ylabel("Mean queue length (requests)", fontsize=11)
    ax2.set_title("(b) Queue Length", fontsize=11)
    ax2.legend(frameon=True, fontsize=8.5, loc="upper left")

    fig.tight_layout()
    _save(fig, "fig6_vt_vs_pt")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 7 · Spearman correlation heatmap
# ─────────────────────────────────────────────────────────────────────────────

def fig7_correlation_heatmap(arr_raw: pd.DataFrame):
    import matplotlib.colors as mcolors

    stable = arr_raw[arr_raw['Rho_Target'] < 1.0].copy()
    cols   = ['Rho_Target', 'Lambda_Achieved_rps', 'Mean_Latency_ms',
              'P99_ms', 'Nq_Hikari_Mean', 'Heap_Mean_MB', 'Threads_Mean']
    labels = ['ρ', 'λ (rps)', 'Mean latency\n(ms)', 'P99\n(ms)',
              'Queue Nq', 'Heap\n(MB)', 'Thread\ncount']
    cols = [c for c in cols if c in stable.columns]
    labels = labels[:len(cols)]

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    fig.suptitle("Spearman Correlation Matrix (stable regime, ρ < 1)", fontsize=13)

    cmap = matplotlib.colormaps.get_cmap("RdYlGn")

    for ax, mode in zip(axes, ["VT", "PT"]):
        sub  = stable[stable['Mode']==mode][cols]
        corr = sub.corr(method='spearman').values
        n    = len(cols)

        im = ax.imshow(corr, cmap=cmap, vmin=-1, vmax=1, aspect="auto")

        ax.set_xticks(range(n)); ax.set_yticks(range(n))
        ax.set_xticklabels(labels, rotation=45, ha="right", fontsize=8.5)
        ax.set_yticklabels(labels, fontsize=8.5)
        ax.set_title(f"Mode = {mode}", fontsize=11, pad=6)

        for i in range(n):
            for j in range(n):
                val = corr[i, j]
                color = "white" if abs(val) > 0.6 else "black"
                ax.text(j, i, f"{val:.2f}", ha="center", va="center",
                        fontsize=8, color=color, fontweight="bold")

        plt.colorbar(im, ax=ax, shrink=0.75, label="Spearman ρ")

    fig.tight_layout()
    _save(fig, "fig7_correlation_heatmap")


# ─────────────────────────────────────────────────────────────────────────────
# Fig 8 · Resource Reallocation — the thesis figure
# ─────────────────────────────────────────────────────────────────────────────

def fig8_resource_reallocation(arr_raw: pd.DataFrame):
    """
    THE thesis figure: blocking does not disappear under Virtual Threads —
    it changes resource currency.

    Plane: x = OS thread inventory, y = waiting requests E[Nq].
    PT trajectory climbs the 1:1 diagonal (each waiting request holds one
    native thread).  VT trajectory is vertical (carrier pool constant; waiting
    requests parked as heap-resident continuations).
    """
    agg = (arr_raw.groupby(['Mode', 'Rho_Target'])
                  .agg(threads=('Threads_Mean', 'mean'),
                       nq=('Nq_Hikari_Mean', 'mean'),
                       heap=('Heap_Mean_MB', 'mean'))
                  .reset_index()
                  .sort_values('Rho_Target'))

    fig, ax = plt.subplots(figsize=(7.5, 5.5))

    # 1:1 reference: Δthreads = ΔNq (blocking denominated in native threads)
    pt0 = agg[(agg['Mode'] == 'PT')].iloc[0]
    x_ref = np.linspace(pt0['threads'], agg['threads'].max() * 1.05, 10)
    ax.plot(x_ref, x_ref - pt0['threads'], color="#bbb", linewidth=1.1,
            linestyle="--", zorder=1,
            label="1 waiting request = 1 native thread")

    for mode, marker, color in [("PT", "s", C["PT"]), ("VT", "o", C["VT"])]:
        sub = agg[agg['Mode'] == mode]
        ax.plot(sub['threads'], sub['nq'], color=color, linewidth=1.6,
                alpha=0.85, zorder=3)
        # Stable points filled, unstable (ρ≥1) open
        st = sub[sub['Rho_Target'] < 1.0]
        un = sub[sub['Rho_Target'] >= 1.0]
        ax.scatter(st['threads'], st['nq'], marker=marker, s=75, color=color,
                   edgecolors="white", linewidths=0.8, zorder=5, label=f"{mode} (ρ < 1)")
        ax.scatter(un['threads'], un['nq'], marker=marker, s=95,
                   facecolors="white", edgecolors=color, linewidths=1.8,
                   zorder=5, label=f"{mode} (ρ = 1.05, unstable)")
        # Direction arrow along the trajectory (second-to-last → last point)
        ax.annotate("", xy=(sub['threads'].iloc[-1], sub['nq'].iloc[-1]),
                    xytext=(sub['threads'].iloc[-2], sub['nq'].iloc[-2]),
                    arrowprops=dict(arrowstyle="-|>", color=color, linewidth=1.4))

    # ρ labels only on the three most informative points per mode (0.85,
    # 0.95, and the unstable 1.05) — the intermediate levels 0.60-0.80 sit in
    # a dense, mostly-overlapping cluster near the PT curve's low end and are
    # deliberately left unlabelled (individual labels there would collide);
    # the "queue-free" callout below covers that range as a group instead.
    label_offsets = {
        ("PT", 0.85): (14, -10), ("PT", 0.95): (14, 6),  ("PT", 1.05): (14, 10),
        ("VT", 0.85): (12, 10),  ("VT", 0.95): (12, -4), ("VT", 1.05): (-14, 14),
    }
    for _, row in agg[agg['Rho_Target'] >= 0.85].iterrows():
        dx, dy = label_offsets.get((row['Mode'], round(row['Rho_Target'], 2)), (10, 8))
        ha = "right" if dx < 0 else "left"
        ax.annotate(f"ρ={row['Rho_Target']:.2f}",
                    (row['threads'], row['nq']),
                    textcoords="offset points", xytext=(dx, dy), ha=ha,
                    fontsize=7, color="#555",
                    bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.75))

    # Thesis annotations. Three text blocks share this plane (VT note, PT
    # note, low-rho cluster note); each is pinned to a distinct vertical
    # band with a white backing box so none of them can collide regardless
    # of exact data extents.
    vt_top = agg[(agg['Mode'] == 'VT')].iloc[-1]
    pt_top = agg[(agg['Mode'] == 'PT')].iloc[-1]
    vt0    = agg[(agg['Mode'] == 'VT')].iloc[0]
    ax.text(vt_top['threads'] + 30, 235,
            "VT: waiting requests parked as\nheap-resident continuations —\n"
            f"carrier pool constant (≈{vt0['threads']:.0f} threads)",
            fontsize=8, color=C["VT"], ha="left", va="top",
            bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="none", alpha=0.85))
    ax.text(pt_top['threads'] * 0.98, pt_top['nq'] + 90,
            "PT: each waiting request\nholds one native thread",
            fontsize=8, color=C["PT"], ha="right", va="bottom",
            bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="none", alpha=0.85))

    # Low/mid-ρ cluster note: covers rho <= 0.80 (unlabelled points). Placed
    # in the empty band below y=0 (below the whole PT/VT low-rho cluster and
    # clear of the rho=0.85/0.95/1.05 point labels above), with the arrow
    # pointing up at the rho=0.30 PT marker (leftmost, isolated point).
    ax.annotate("ρ ≤ 0.80: queue-free to onset,\nVT & PT nearly overlapping\n(individual points unlabelled)",
                xy=(pt0['threads'], pt0['nq']), xytext=(pt0['threads'] + 40, -38),
                fontsize=7.5, color="#777", ha="left", va="center",
                bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="none", alpha=0.85),
                arrowprops=dict(arrowstyle="-", color="#aaa", linewidth=0.8,
                                 connectionstyle="arc3,rad=0.25"))

    ax.set_xlabel("OS thread inventory (mean native threads)", fontsize=11)
    ax.set_ylabel("Waiting requests  E[Nq]  (blocking inventory)", fontsize=11)
    ax.set_title("Blocking Does Not Disappear — It Changes Resource Currency",
                 fontsize=12)
    ax.legend(loc="upper center", frameon=True, fontsize=8)
    ax.set_xlim(80, agg['threads'].max() * 1.12)
    ax.set_ylim(-60, agg['nq'].max() * 1.15)

    fig.tight_layout()
    _save(fig, "fig8_resource_reallocation")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def run():
    D        = load_all()
    arr_val  = D['arr_val']
    arr_raw  = D['arr_raw']
    arr_sum  = D['arr_sum']
    arr_bnd  = D['arr_bound']

    print("Generating figures...")
    fig1_operating_envelope(arr_bnd)
    fig2_latency_vs_rho(arr_val, arr_sum, arr_raw)
    fig3_calibration(arr_val)
    fig4_residual(arr_val, arr_raw)
    fig5_bland_altman(arr_val)
    fig6_vt_vs_pt(arr_raw, arr_val)
    fig7_correlation_heatmap(arr_raw)
    fig8_resource_reallocation(arr_raw)
    print(f"\n✓ All 8 figures saved to {OUTPUT_DIR}/")


if __name__ == "__main__":
    run()

