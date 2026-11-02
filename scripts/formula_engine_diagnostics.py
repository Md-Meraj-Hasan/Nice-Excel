#!/usr/bin/env python3
"""
NiceExcel formula-engine CI diagnostics.

Runs a real spreadsheet calculation through LibreOffice Calc on the CI runner,
checks the cached results, and inspects the Android APK/source to report which
engines are wired and whether the Android LibreOffice native core is packaged.

IMPORTANT: host Libreoffice success proves formula semantics in CI, NOT that the
Android JNI runtime works on a device. Device JNI execution is separately
identified as NOT_EXECUTED in the JSON report.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

try:
    from openpyxl import Workbook, load_workbook
except ImportError as exc:
    raise SystemExit("Missing Python dependency openpyxl. Install it before running diagnostics.") from exc


def run(command: list[str], timeout: int = 180) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout, check=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, help="APK produced by assembleDebug")
    parser.add_argument("--report", default="build/reports/formula-engine-diagnostics.json")
    parser.add_argument("--source-root", default=".")
    args = parser.parse_args()

    root = Path(args.source_root).resolve()
    apk = Path(args.apk).resolve()
    report_path = Path(args.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report: dict = {
        "schema": "niceexcel.formula-engine-diagnostics.v1",
        "ci_host_formula_engine": {"name": None, "connected": False, "version": None,
                                   "tests": [], "status": "NOT_RUN"},
        "android_apk": {"path": str(apk), "exists": apk.is_file(),
                        "libreoffice_arm64_native_core": False,
                        "libreoffice_java_bootstrap": False},
        "source_wiring": {"libreoffice_bridge": False, "poi_fallback": False},
        "android_device_jni_calculation": "NOT_EXECUTED_BY_HOST_CI",
        "summary": "NOT_RUN",
    }
    failures: list[str] = []

    soffice = shutil.which("libreoffice") or shutil.which("soffice")
    if soffice:
        version = run([soffice, "--version"], timeout=30)
        report["ci_host_formula_engine"].update(
            name="LibreOffice Calc", connected=version.returncode == 0,
            version=version.stdout.strip(), status="RUNNING")
        expected = {
            "B1": 3.0, "B2": 60.0, "B3": "OK", "B4": "Lookup Passed",
        }
        with tempfile.TemporaryDirectory(prefix="niceexcel-formula-ci-") as temp:
            temp_path = Path(temp)
            source = temp_path / "formula-engine-input.xlsx"
            output_dir = temp_path / "recalculated"
            output_dir.mkdir()
            wb = Workbook()
            ws = wb.active
            ws.title = "EngineCheck"
            ws["A1"] = 1
            ws["A2"] = 2
            ws["A3"] = 3
            ws["B1"] = "=SUM(A1:A2)"
            ws["B2"] = "=SUM(A1:A3)*10"
            ws["B3"] = '=IF(B1=3,"OK","FAIL")'
            ws["A5"] = 102
            ws["B5"] = "Alpha"
            ws["A6"] = 105
            ws["B6"] = "Lookup Passed"
            ws["A7"] = 108
            ws["B7"] = "Gamma"
            ws["B4"] = "=VLOOKUP(105,A5:B7,2,0)"
            ws["B8"] = "=1/0"
            wb.calculation.fullCalcOnLoad = True
            wb.calculation.forceFullCalc = True
            wb.calculation.calcMode = "auto"
            wb.save(source)

            profile = (temp_path / "lo-profile").as_uri()
            # Round-trip through ODS so Calc really loads, recalculates, and saves the workbook.
            ods_dir = temp_path / "recalculated-ods"
            ods_dir.mkdir()
            result = run([
                soffice, "-env:UserInstallation=" + profile, "--headless",
                "--convert-to", "ods", "--outdir", str(ods_dir), str(source)
            ])
            ods_file = ods_dir / (source.stem + ".ods")
            if ods_file.is_file():
                result = run([
                    soffice, "-env:UserInstallation=" + profile, "--headless",
                    "--convert-to", "xlsx", "--outdir", str(output_dir), str(ods_file)
                ])
            calculated_file = output_dir / source.name

