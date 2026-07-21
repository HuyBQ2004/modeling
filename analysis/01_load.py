"""
01_load.py
==========
Data loading and pre-processing for all CSV files in D:\\research_2\\data.
Exports clean DataFrames used by downstream analysis scripts.

Updated 2026-07-19:
  - Uses latest timestamps: MSSQL 20260715_181339, PostgreSQL 20260716_105611
  - Loads both MSSQL and PostgreSQL for cross-DBMS generalizability analysis
  - Adds HTTP sweep loaders (Study 3) for both databases
  - n=20 replicates per cell (updated from earlier n=5 runs)

Run standalone to verify data integrity:
    python 01_load.py
"""

import pandas as pd
import numpy as np
from pathlib import Path

# ── Paths (portable: fall back to repo-relative paths if D:\\ not available) ──
DATA_DIR   = Path(r"D:\research_2\data")
OUTPUT_DIR = Path(r"D:\research_2\analysis\paper")
if not DATA_DIR.exists():
    _ROOT      = Path(__file__).resolve().parent.parent
    DATA_DIR   = _ROOT / "data"
    OUTPUT_DIR = _ROOT / "analysis" / "paper"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# ── File manifest (latest timestamps) ──────────────────────────────────────────
F = {
    # Study 1 — ArrivalSweep — MSSQL (primary)
    "arr_raw_mssql":   DATA_DIR / "ARRIVAL_SWEEP_RAW_20260715_181339_MSSQL.csv",
    "arr_sum_mssql":   DATA_DIR / "ARRIVAL_SWEEP_SUMMARY_20260715_181339_MSSQL.csv",
    "arr_val_mssql":   DATA_DIR / "ARRIVAL_SWEEP_VALIDATION_20260715_181339_MSSQL.csv",
    "arr_bound_mssql": DATA_DIR / "ARRIVAL_SWEEP_BOUNDARIES_20260715_181339_MSSQL.csv",
    "arr_pred_mssql":  DATA_DIR / "ARRIVAL_SWEEP_PREDICTIONS_20260715_181339_MSSQL.csv",

    # Study 1 — ArrivalSweep — PostgreSQL (cross-DBMS validation)
    "arr_raw_pg":      DATA_DIR / "ARRIVAL_SWEEP_RAW_20260716_105611_POSTGRESQL.csv",
    "arr_sum_pg":      DATA_DIR / "ARRIVAL_SWEEP_SUMMARY_20260716_105611_POSTGRESQL.csv",
    "arr_val_pg":      DATA_DIR / "ARRIVAL_SWEEP_VALIDATION_20260716_105611_POSTGRESQL.csv",
    "arr_bound_pg":    DATA_DIR / "ARRIVAL_SWEEP_BOUNDARIES_20260716_105611_POSTGRESQL.csv",
    "arr_pred_pg":     DATA_DIR / "ARRIVAL_SWEEP_PREDICTIONS_20260716_105611_POSTGRESQL.csv",

    # Study 2 — PoolSweep — MSSQL
    "pool_raw":        DATA_DIR / "POOL_SWEEP_RAW_20260715_162055_MSSQL.csv",
    "pool_sum":        DATA_DIR / "POOL_SWEEP_SUMMARY_20260715_162055_MSSQL.csv",
    "pool_val":        DATA_DIR / "POOL_SWEEP_VALIDATION_20260715_162055_MSSQL.csv",
    "pool_alpha":      DATA_DIR / "POOL_SWEEP_ALPHA_20260715_162055_MSSQL.csv",
    "pool_xval":       DATA_DIR / "POOL_SWEEP_CROSSVAL_20260715_162055_MSSQL.csv",

    # Study 3 — HTTP sweep — MSSQL
    "http_raw_mssql":  DATA_DIR / "HTTP_SWEEP_RAW_20260717_145805_MSSQL.csv",
    "http_sum_mssql":  DATA_DIR / "HTTP_SWEEP_SUMMARY_20260717_145805_MSSQL.csv",

    # Study 3 — HTTP sweep — PostgreSQL
    "http_raw_pg":     DATA_DIR / "HTTP_SWEEP_RAW_20260717_193728_POSTGRESQL.csv",
    "http_sum_pg":     DATA_DIR / "HTTP_SWEEP_SUMMARY_20260717_193728_POSTGRESQL.csv",
}

# Backward-compat aliases (old scripts import arr_raw, arr_sum, arr_val …)
# Default to MSSQL (primary study)
F["arr_raw"]   = F["arr_raw_mssql"]
F["arr_sum"]   = F["arr_sum_mssql"]
F["arr_val"]   = F["arr_val_mssql"]
F["arr_bound"] = F["arr_bound_mssql"]
F["arr_pred"]  = F["arr_pred_mssql"]


# ── Helpers ───────────────────────────────────────────────────────────────────

def _pct(col: pd.Series) -> pd.Series:
    """Strip '%' suffix and cast to float."""
    return col.astype(str).str.rstrip('%').replace('NA', np.nan).astype(float)


def _pm(col: pd.Series) -> pd.Series:
    """Strip '±' prefix and cast to float."""
    return col.astype(str).str.replace('±', '', regex=False).replace('NA', np.nan).astype(float)


def _load_arr_raw(path: Path) -> pd.DataFrame:
    """Raw arrival sweep – one row per rep per mode per ρ."""
    df = pd.read_csv(path)
    df = df.rename(columns=lambda c: c.strip())
    df['Stable'] = df['Rho_Target'] < 1.0
    # Normalise column name differences between runs
    for old, new in [("Nq_Hikari_Mean", "Nq_Hikari_Mean"),
                     ("P99_ms_Mean", "P99_ms"),
                     ("Lambda_Achieved_Mean", "Lambda_Achieved_rps")]:
        if old in df.columns and new not in df.columns:
            df = df.rename(columns={old: new})
    return df


def _load_arr_sum(path: Path) -> pd.DataFrame:
    """Arrival sweep summary – mean per (Mode, Rho_Target)."""
    df = pd.read_csv(path)
    df = df.rename(columns=lambda c: c.strip())
    df['Stable'] = df['Rho_Target'] < 1.0
    return df


def _load_arr_val(path: Path) -> pd.DataFrame:
    """
    Arrival sweep validation – model prediction vs measured.
    Drops UNSTABLE rows (rho=1.05).
    """
    df = pd.read_csv(path, engine="python", on_bad_lines="skip")
    df = df.rename(columns=lambda c: c.strip())
    df = df[df['Validation_Pass'].isin(['PASS', 'FAIL'])].copy()
    df['Latency_Error_Pct']     = _pct(df['Latency_Error_Pct'])
    df['Nq_Error_Pct']          = _pct(df['Nq_Error_Pct'])
    df['CI95_Latency_ms']       = pd.to_numeric(df['CI95_Latency_ms'], errors='coerce')
    df['CI95_Nq']               = pd.to_numeric(df['CI95_Nq'],          errors='coerce')
    df['Model_Mean_Latency_ms'] = pd.to_numeric(df['Model_Mean_Latency_ms'], errors='coerce')
    df['Model_E_Nq']            = pd.to_numeric(df['Model_E_Nq'],       errors='coerce')
    df['Measured_Nq']           = pd.to_numeric(df['Measured_Nq'],      errors='coerce')
    df['Stable'] = df['Rho_Target'] < 1.0
    return df


def _load_arr_boundaries(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path)
    return df.rename(columns=lambda c: c.strip())


def _load_arr_pred(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, engine="python", on_bad_lines="skip")
    df = df.rename(columns=lambda c: c.strip())
    if 'Model_Valid' in df.columns:
        df = df[df['Model_Valid'] != 'NO_(rho>=1)'].copy()
    return df


def _load_http_sum(path: Path) -> pd.DataFrame:
    """HTTP sweep summary – same structure as arr_sum."""
    df = pd.read_csv(path)
    df = df.rename(columns=lambda c: c.strip())
    df['Stable'] = df['Rho_Target'] < 1.0
    return df


def _load_http_raw(path: Path) -> pd.DataFrame:
    """HTTP sweep raw – one row per rep per mode per ρ."""
    df = pd.read_csv(path)
    df = df.rename(columns=lambda c: c.strip())
    df['Stable'] = df['Rho_Target'] < 1.0
    return df


# ── Public loaders (MSSQL) ────────────────────────────────────────────────────

def load_arr_raw()   -> pd.DataFrame: return _load_arr_raw(F["arr_raw_mssql"])
def load_arr_sum()   -> pd.DataFrame: return _load_arr_sum(F["arr_sum_mssql"])
def load_arr_val()   -> pd.DataFrame: return _load_arr_val(F["arr_val_mssql"])
def load_arr_boundaries() -> pd.DataFrame: return _load_arr_boundaries(F["arr_bound_mssql"])
def load_arr_pred()  -> pd.DataFrame: return _load_arr_pred(F["arr_pred_mssql"])

# ── Public loaders (PostgreSQL) ───────────────────────────────────────────────

def load_arr_raw_pg()   -> pd.DataFrame: return _load_arr_raw(F["arr_raw_pg"])
def load_arr_sum_pg()   -> pd.DataFrame: return _load_arr_sum(F["arr_sum_pg"])
def load_arr_val_pg()   -> pd.DataFrame: return _load_arr_val(F["arr_val_pg"])
def load_arr_boundaries_pg() -> pd.DataFrame: return _load_arr_boundaries(F["arr_bound_pg"])
def load_arr_pred_pg()  -> pd.DataFrame: return _load_arr_pred(F["arr_pred_pg"])

# ── Pool sweep loaders ────────────────────────────────────────────────────────

def load_pool_raw() -> pd.DataFrame:
    df = pd.read_csv(F["pool_raw"])
    return df.rename(columns=lambda c: c.strip())

def load_pool_sum() -> pd.DataFrame:
    df = pd.read_csv(F["pool_sum"])
    df = df.rename(columns=lambda c: c.strip())
    for col in ['CI95_P99_ms', 'CI95_Latency_ms', 'CI95_Heap_MB']:
        if col in df.columns:
            df[col] = _pm(df[col])
    return df

def load_pool_val() -> pd.DataFrame:
    df = pd.read_csv(F["pool_val"])
    df = df.rename(columns=lambda c: c.strip())
    df['Latency_Error_Pct'] = _pct(df['Latency_Error_Pct'])
    df['Heap_Error_Pct']    = _pct(df['Heap_Error_Pct'])
    for col in ['CI95_Latency_ms', 'CI95_P99_ms', 'CI95_Heap_MB']:
        if col in df.columns:
            df[col] = _pm(df[col])
    return df

def load_pool_alpha() -> pd.DataFrame:
    df = pd.read_csv(F["pool_alpha"])
    return df.rename(columns=lambda c: c.strip())

def load_pool_xval() -> pd.DataFrame:
    df = pd.read_csv(F["pool_xval"])
    df = df.rename(columns=lambda c: c.strip())
    df['Latency_Error_Pct'] = _pct(df['Latency_Error_Pct'])
    df['Heap_Error_Pct']    = _pct(df['Heap_Error_Pct'])
    for col in ['CI95_Latency_ms', 'CI95_P99_ms', 'CI95_Heap_MB']:
        if col in df.columns:
            df[col] = _pm(df[col])
    return df

# ── HTTP loaders ──────────────────────────────────────────────────────────────

def load_http_sum_mssql() -> pd.DataFrame: return _load_http_sum(F["http_sum_mssql"])
def load_http_sum_pg()    -> pd.DataFrame: return _load_http_sum(F["http_sum_pg"])

def load_http_raw_mssql() -> pd.DataFrame:
    if F["http_raw_mssql"].exists():
        return _load_http_raw(F["http_raw_mssql"])
    return pd.DataFrame()

def load_http_raw_pg() -> pd.DataFrame:
    if F["http_raw_pg"].exists():
        return _load_http_raw(F["http_raw_pg"])
    return pd.DataFrame()


# ── load_all ──────────────────────────────────────────────────────────────────

def load_all() -> dict:
    """Return all DataFrames in a dict keyed by logical name."""
    data = {
        # MSSQL primary (Study 1)
        'arr_raw':        load_arr_raw(),
        'arr_sum':        load_arr_sum(),
        'arr_val':        load_arr_val(),
        'arr_bound':      load_arr_boundaries(),
        'arr_pred':       load_arr_pred(),
        # PostgreSQL (cross-DBMS)
        'arr_raw_pg':     load_arr_raw_pg(),
        'arr_sum_pg':     load_arr_sum_pg(),
        'arr_val_pg':     load_arr_val_pg(),
        'arr_bound_pg':   load_arr_boundaries_pg(),
        'arr_pred_pg':    load_arr_pred_pg(),
        # Pool sweep (Study 2)
        'pool_raw':       load_pool_raw(),
        'pool_sum':       load_pool_sum(),
        'pool_val':       load_pool_val(),
        'pool_alpha':     load_pool_alpha(),
        'pool_xval':      load_pool_xval(),
        # HTTP (Study 3)
        'http_sum_mssql': load_http_sum_mssql(),
        'http_sum_pg':    load_http_sum_pg(),
    }
    return data


# ── Main ─────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("Loading all data files...\n")
    data = load_all()
    for name, df in data.items():
        print(f"  [{name}]  shape={df.shape}  columns={list(df.columns)}")
    print("\n✓  Data loading OK — all files parsed successfully.")
