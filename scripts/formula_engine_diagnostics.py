#!/usr/bin/env python3
"""CI diagnostics: run real Calc formulas and inspect NiceExcel APK engine packaging.

Host Calc checks formula semantics. It does not claim Android JNI was executed;
that requires the APK to run on an ARM64 Android device.
"""
from __future__ import annotations
import argparse, json, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path
from openpyxl import Workbook, load_workbook

def command(args: list[str], timeout: int = 180) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout, check=False)

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--report", default="build/reports/formula-engine-diagnostics.json")
    parser.add_argument("--source-root", default=".")
    args = parser.parse_args()
    root, apk = Path(args.source_root).resolve(), Path(args.apk).resolve()
    report_path = Path(args.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "schema": "niceexcel.formula-engine-diagnostics.v1",
        "ci_host_formula_engine": {"name": None, "connected": False, "version": None, "tests": [], "status": "NOT_RUN"},
        "android_apk": {"path": str(apk), "exists": apk.is_file(), "libreoffice_arm64_native_core": False},
        "source_wiring": {"libreoffice_bridge": False, "poi_formula_reader": False},
        "android_device_jni_calculation": "NOT_EXECUTED_BY_HOST_CI",
        "summary": "FAIL", "failures": []
    }
    failures: list[str] = []
    soffice = shutil.which("libreoffice") or shutil.which("soffice")
    if not soffice:
        failures.append("LibreOffice Calc executable is not installed")
        report["ci_host_formula_engine"]["status"] = "ENGINE_NOT_INSTALLED"
    else:
        version = command([soffice, "--version"], 30)
        host = report["ci_host_formula_engine"]
        host.update(name="LibreOffice Calc", connected=version.returncode == 0,
                    version=version.stdout.strip(), status="RUNNING")
        if version.returncode:
            failures.append("LibreOffice --version failed: " + version.stdout[-1000:])
        else:
            with tempfile.TemporaryDirectory(prefix="niceexcel-calc-ci-") as tmp:
                tmp = Path(tmp)
                src = tmp / "engine-check.xlsx"
                ods_dir, xlsx_dir = tmp / "ods", tmp / "xlsx"
                ods_dir.mkdir(); xlsx_dir.mkdir()
                wb = Workbook()
                ws = wb.active; ws.title = "EngineCheck"
                for cell, value in {
                    "A1": 1, "A2": 2, "A3": 3,
                    "B1": "=SUM(A1:A2)", "B2": "=SUM(A1:A3)*10",
                    "B3": '=IF(B1=3,"OK","FAIL")',
                    "A5": 102, "B5": "Alpha", "A6": 105,
                    "B6": "Lookup Passed", "A7": 108, "B7": "Gamma",
                    "B4": "=VLOOKUP(105,A5:B7,2,0)", "B8": "=1/0"
                }.items():
                    ws[cell] = value
                wb.calculation.fullCalcOnLoad = True
                wb.calculation.forceFullCalc = True
                wb.calculation.calcMode = "auto"
                wb.save(src)
                profile = (tmp / "lo-profile").as_uri()
                to_ods = command([soffice, "-env:UserInstallation="+profile, "--headless",
                                  "--convert-to", "ods", "--outdir", str(ods_dir), str(src)])
                ods_file = ods_dir / "engine-check.ods"
                if to_ods.returncode or not ods_file.is_file():
                    failures.append("Calc XLSX-to-ODS recalculation failed: " + to_ods.stdout[-1500:])
                else:
                    to_xlsx = command([soffice, "-env:UserInstallation="+profile, "--headless",
                                       "--convert-to", "xlsx", "--outdir", str(xlsx_dir), str(ods_file)])
                    out = xlsx_dir / "engine-check.xlsx"
                    if to_xlsx.returncode or not out.is_file():
                        failures.append("Calc ODS-to-XLSX save failed: " + to_xlsx.stdout[-1500:])
                    else:
                        formulas = load_workbook(out, data_only=False)["EngineCheck"]
                        values = load_workbook(out, data_only=True)["EngineCheck"]
                        checks = [
                            ("B1", "=SUM(A1:A2)", 3),
                            ("B2", "=SUM(A1:A3)*10", 60),
                            ("B3", '=IF(B1=3,"OK","FAIL")', "OK"),
                            ("B4", "=VLOOKUP(105,A5:B7,2,0)", "Lookup Passed"),
                            ("B8", "=1/0", "#DIV/0!")
                        ]
                        for cell, formula, expected in checks:
                            actual = values[cell].value
                            passed = actual == expected or (
                                isinstance(expected, (int,float)) and isinstance(actual,(int,float))
                                and abs(actual-expected) < 1e-9)
                            host["tests"].append({"cell":cell,"formula":formula,
                                "expected":expected,"actual":actual,"passed":passed})
                            if not passed:
                                failures.append(f"Calc formula {cell}: expected {expected!r}, got {actual!r}")
                        formulas.parent.close(); values.parent.close()
                        host["status"] = "PASS" if not any("Calc formula" in x for x in failures) else "FAIL"

    engine = root / "app/src/main/java/com/meraj/niceexcel/LibreOfficeCalcEngine.kt"
    service = root / "app/src/main/java/com/meraj/niceexcel/LibreOfficeEngineService.java"
    main_activity = root / "app/src/main/java/com/meraj/niceexcel/MainActivity.kt"
    engine_text = engine.read_text(encoding="utf-8") if engine.is_file() else ""
    service_text = service.read_text(encoding="utf-8") if service.is_file() else ""
    main_text = main_activity.read_text(encoding="utf-8") if main_activity.is_file() else ""
    report["source_wiring"]["libreoffice_bridge"] = (
        "recalculateSnapshot" in engine_text and "LibreOfficeCalcEngine" in service_text)
    report["source_wiring"]["poi_formula_reader"] = (
        "WorkbookFactory" in engine_text and ("FormulaEvaluator" in main_text or "evaluateFormula" in main_text))
    if not report["source_wiring"]["libreoffice_bridge"]:
        failures.append("LibreOffice service/engine bridge source check failed")

    if not apk.is_file():
        failures.append("APK not found: " + str(apk))
    else:
        try:
            with zipfile.ZipFile(apk) as z:
                names = z.namelist()
                found = any(n.endswith("lib/arm64-v8a/liblo-native-code.so") for n in names)
                report["android_apk"]["libreoffice_arm64_native_core"] = found
                report["android_apk"]["engine_packaging"] = (
                    "ARM64_NATIVE_CORE_PACKAGED" if found else "BRIDGE_ONLY_NO_ARM64_NATIVE_CORE")
                report["android_apk"]["arm64_native_libraries"] = [
                    n for n in names if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
        except zipfile.BadZipFile:
            failures.append("APK is not a valid ZIP archive")

    report["failures"] = failures
    report["summary"] = "PASS_HOST_FORMULAS_DEVICE_RUNTIME_PENDING" if not failures else "FAIL"
    report_path.write_text(json.dumps(report, indent=2, default=str) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, default=str), flush=True)
    print("DIAGNOSTICS_REPORT=" + str(report_path), flush=True)
    return 1 if failures else 0

if __name__ == "__main__":
    raise SystemExit(main())
