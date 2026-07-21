"""
run_all.py
==========
Master runner: executes the full analysis pipeline in order.

Usage:
    python run_all.py            # run everything
    python run_all.py --steps 1 2 3   # run specific steps

Prerequisites (one-time install):
    pip install pandas numpy scipy matplotlib
"""

import sys
import importlib
import importlib.util
import argparse
import time
from pathlib import Path

ROOT = Path(__file__).parent
sys.path.insert(0, str(ROOT))


def _load_module(filename):
    """Dynamically load a script module from the analysis folder."""
    stem = filename.replace(".py", "")
    # Register 01_load as 'load_01' for other scripts to import
    if stem == "01_load":
        alias = "load_01"
    else:
        alias = stem.replace("-", "_")
    spec = importlib.util.spec_from_file_location(alias, ROOT / filename)
    mod  = importlib.util.module_from_spec(spec)
    sys.modules[alias] = mod
    spec.loader.exec_module(mod)
    return mod


STEPS = [
    (1, "01_load.py",      "Data loading & validation"),
    (2, "02_validation.py","Validation metrics + LaTeX tables"),
    (3, "03_statistics.py","Effect size, breakpoint, correlation"),
    (4, "04_figures.py",   "Publication figures (7 figures)"),
]


def main():
    parser = argparse.ArgumentParser(description="Research analysis pipeline")
    parser.add_argument("--steps", nargs="*", type=int,
                        help="Which steps to run (default: all)")
    args = parser.parse_args()

    run_steps = set(args.steps) if args.steps else {s for s,_,_ in STEPS}

    print("=" * 60)
    print("  RESEARCH ANALYSIS PIPELINE")
    print("  Queueing-Based Resource Saturation Model")
    print("=" * 60)

    total_t0 = time.time()
    for (step_num, filename, description) in STEPS:
        if step_num not in run_steps:
            print(f"\n[{step_num}] SKIP  {description}")
            continue

        print(f"\n[{step_num}] RUNNING  {description}")
        print(f"      {filename}")
        print("-" * 50)
        t0 = time.time()
        try:
            mod = _load_module(filename)
            if hasattr(mod, "run"):
                mod.run()
            else:
                # Script runs on import (module-level code)
                pass
            elapsed = time.time() - t0
            print(f"\n  OK  Step {step_num} done in {elapsed:.1f}s")
        except Exception as exc:
            print(f"\n  FAILED  Step {step_num}: {exc}")
            import traceback
            traceback.print_exc()

    total = time.time() - total_t0
    print("\n" + "=" * 60)
    print(f"  Pipeline complete in {total:.1f}s")
    print(f"  Outputs -> {ROOT}/paper/")
    print("=" * 60)


if __name__ == "__main__":
    main()
