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
            result = run([
                soffice, "-env:UserInstallation=" + profile, "--headless",
                "--convert-to", "xlsx", "--outdir", str(output_dir), str(source)
            ])
            calculated_file = output_dir / source.name
            # LibreOffice may decline conversion when source and target formats match.
            # In that case open/save through a temporary ODS conversion is not used;
            # instead invoke its headless Python-free CLI via a macro-free xlsx copy.
            if not calculated_file.is_file():
                retry_dir = temp_path / "retry"
                retry_dir.mkdir()
                result = run([
                    soffice, "-env:UserInstallation=" + (temp_path / "lo-profile-retry").as_uri(),
                    "--headless", "--convert-to", "xlsx", "--outdir", str(retry_dir), str(source)
                ])
                calculated_file = retry_dir / source.name

            if not calculated_file.is_file():
                failures.append("LibreOffice did not produce the recalculated XLSX: " + result.stdout[-2000:])
            else:
                values = load_workbook(calculated_file, data_only=True)
                calc = values["EngineCheck"]
                actual = {cell: calc[cell].value for cell in expected}
                for cell, wanted in expected.items():
                    got = actual[cell]
                    passed = (abs(got - wanted) < 1e-9) if isinstance(wanted, float) and isinstance(got, (int, float)) else got == wanted
                    report["ci_host_formula_engine"]["tests"].append({
                        "cell": cell, "formula": load_workbook(calculated_file, data_only=False)["EngineCheck"][cell].value,
                        "expected": wanted, "actual": got, "passed": passed})
                    if not passed:
                        failures.append(f"{cell}: expected {wanted!r}, got {got!r}")
                error_value = calc["B8"].value
                error_passed = isinstance(error_value, str) and error_value.startswith("#DIV/0!")
                report["ci_host_formula_engine"]["tests"].append({
                    "cell": "B8", "formula": "=1/0", "expected": "#DIV/0!",
                    "actual": error_value, "passed": error_passed})
                if not error_passed:
                    failures.append(f"B8: expected #DIV/0!, got {error_value!r}")
                values.close()
                report["ci_host_formula_engine"]["status"] = "PASS" if not failures else "FAIL"
    else:
        failures.append("LibreOffice/soffice executable is not installed on the CI runner.")
        report["ci_host_formula_engine"]["status"] = "ENGINE_NOT_INSTALLED"

    if apk.is_file():
        try:
            with zipfile.ZipFile(apk) as archive:
                names = archive.namelist()
                report["android_apk"]["libreoffice_arm64_native_core"] = any(
                    name.endswith("lib/arm64-v8a/liblo-native-code.so") for name in names)
                report["android_apk"]["libreoffice_java_bootstrap"] = any(
                    name.endswith("classes.dex") for name in names) and (
                    root / "app/src/main/java/org/libreoffice/kit/LibreOfficeKit.java").is_file()
                report["android_apk"]["native_libraries"] = sorted(
                    name for name in names if name.startswith("lib/arm64-v8a/") and name.endswith(".so"))
        except zipfile.BadZipFile:
            failures.append("APK is not a valid ZIP/APK archive.")
    else:
        failures.append(f"APK does not exist: {apk}")

    engine_source = root / "app/src/main/java/com/meraj/niceexcel/LibreOfficeCalcEngine.kt"
    service_source = root / "app/src/main/java/com/meraj/niceexcel/LibreOfficeEngineService.java"
    main_source = root / "app/src/main/java/com/meraj/niceexcel/MainActivity.kt"
    engine_text = engine_source.read_text(encoding="utf-8") if engine_source.is_file() else ""
    service_text = service_source.read_text(encoding="utf-8") if service_source.is_file() else ""
    main_text = main_source.read_text(encoding="utf-8") if main_source.is_file() else ""
    report["source_wiring"]["libreoffice_bridge"] = (
        "recalculateSnapshot" in engine_text and "LibreOfficeCalcEngine" in service_text
        and "LO_INIT_BEGIN" in engine_text)
    report["source_wiring"]["poi_fallback"] = (
        "WorkbookFactory" in engine_text and ("FormulaEvaluator" in main_text or "POI" in main_text))
    if not report["source_wiring"]["libreoffice_bridge"]:
        failures.append("LibreOffice bridge wiring/signature checks did not pass.")

    report["android_apk"]["engine_link_status"] = (
        "NATIVE_CORE_PACKAGED" if report["android_apk"]["libreoffice_arm64_native_core"]
        else "JAVA_BRIDGE_PRESENT_BUT_NATIVE_CORE_NOT_PACKAGED")
    report["summary"] = "PASS_WITH_DEVICE_RUNTIME_PENDING" if not failures else "FAIL"
    report["failures"] = failures
    report_path.write_text(json.dumps(report, indent=2, default=str) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, default=str))
    print(f"DIAGNOSTICS_REPORT={report_path}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
