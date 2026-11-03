from pathlib import Path

ROOT = Path(__file__).resolve().parents[1].parent

def test_native_and_web_html_have_compact_ribbon_and_preserve_tools():
    files = [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]
    required_ids = ["newBtn", "saveBtn", "undoBtn", "boldBtn", "align", "numberFormat", "calculateBtn", "nativeExcelBtn"]
    for path in files:
        html = path.read_text(encoding="utf-8")
        assert "setupCompactRibbon" in html
        assert "toolgroup[open] .toolmenu{display:flex}" in html
        for element_id in required_ids:
            assert f'id="{element_id}"' in html, f"missing existing tool {element_id} in {path}"

def test_web_ribbon_keeps_native_switch_handler():
    html = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    assert "NiceExcelNative.goNative" in html


def test_expanded_ribbon_exposes_more_working_tools_in_both_clients():
    files = [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]
    required_ids = [
        "copyCellBtn", "pasteCellBtn", "clearCellBtn", "insertRowBtn", "mergeBtn",
        "chartBtn", "fontSizeBtn", "clearFormatBtn", "sortAscBtn", "sortDescBtn",
        "filterColumnBtn", "clearFilterBtn", "formulaPickerRibbonBtn",
    ]
    for path in files:
        html = path.read_text(encoding="utf-8")
        for element_id in required_ids:
            assert f'id="{element_id}"' in html, f"missing expanded tool {element_id} in {path}"
        for handler in ["copyCellBtn').onclick", "pasteCellBtn').onclick", "clearCellBtn').onclick", "sortAscBtn').onclick", "filterColumnBtn').onclick"]:
            assert handler in html, f"missing working handler {handler} in {path}"


def test_native_advanced_ribbon_exposes_and_wires_advanced_tools():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert '"Advanced"' in native
    for label in [
        "Open Full Advanced Tools (Web)",
        "Conditional Formatting…",
        "Data Validation…",
        "Data Cleaning…",
        "Filter by Cell Colour",
        "Pivot Summary…",
        "Dashboard Builder…",
    ]:
        assert label in native, f"missing native Advanced tool: {label}"
    manifest = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "AndroidManifest.xml").read_text(encoding="utf-8")
    assert 'android:name=".FlaskActivity" android:exported="false"' in manifest
    for handler in [
        "startActivity(Intent(this, FlaskActivity::class.java))",
        "conditionalFormattingMenu()",
        "dataValidationMenu()",
        "dataCleaningMenu()",
        "filterByCellColour()",
        "pivotSummaryMenu(false)",
        "pivotSummaryMenu(true)",
    ]:
        assert handler in native, f"missing native Advanced action: {handler}"
