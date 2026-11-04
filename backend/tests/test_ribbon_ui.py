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
    manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
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


def test_native_table_tools_accept_custom_ranges_and_apply_row_column_borders():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert 'hint = "Enter range, e.g. A1:D20"' in native
    assert '.setView(panel)' in native
    assert 'dialog.show()' in native
    assert 'applyTableRangeAction(index, cells, range)' in native
    assert "cells.forEach { address ->" in native
    assert "thickBorder = true" in native
    assert "Table style applied to $range" in native


def test_native_filters_support_multiple_columns_and_clear_individual_criteria():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert "private val filterCriteria = linkedMapOf<String, String>()" in native
    assert "criteria.all { (column, query) ->" in native
    assert "filterCriteria.remove(col)" in native
    assert "Filters applied:" in native


def test_web_and_android_webview_quick_filters_support_multiple_columns():
    for path in [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]:
        html = path.read_text(encoding="utf-8")
        assert "let quickFilters=[]" in html
        assert "for(const f of quickFilters)" in html
        # The touch-friendly dialog trims input before adding a per-column
        # criterion, while preserving any other selected column criteria.
        assert "const q=valueInput.value.trim()" in html
        assert "quickFilters=quickFilters.filter(f=>f.column!==column)" in html
        assert "quickFilters.push({column,text:q})" in html
        assert "quickFilters=[]" in html


def test_native_black_theme_keeps_existing_cell_formula_and_inline_editor_text_readable():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert "if (luminance < 145) Color.rgb(235,241,250) else color" in native
    assert "setTextColor(themedCellTextColor(Color.rgb(24,32,39)))" in native
    assert "setHintTextColor(if (darkMode) Color.rgb(150,165,185)" in native


def test_native_table_borders_use_hairline_instead_of_double_dp_strokes():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert "else if (isTableBorder) 1 else dp(1)" in native
    assert "using a dp-scaled 2dp stroke on both" in native


def test_native_black_theme_uses_dark_cells_and_persists_palette_for_web_activity():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    flask = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "FlaskActivity.kt").read_text(encoding="utf-8")
    assert 'add(if (darkMode) "Switch to Day Theme" to { toggleDarkTheme() } else "Switch to Black Theme" to { toggleDarkTheme() })' in native
    assert "Color.BLACK" in native and "Color.rgb(65,82,106)" in native
    assert 'putBoolean("dark_mode", darkMode)' in native
    assert 'getSharedPreferences("niceexcel_theme", MODE_PRIVATE)' in flask
    assert 'window.statusBarColor = if (blackTheme) Color.BLACK else systemAccent' in flask
    assert 'window.statusBarColor = if (darkMode) Color.BLACK' in native
    assert 'window.navigationBarColor = if (darkMode) Color.BLACK' in native


def test_native_filters_match_displayed_formula_results_in_any_column():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert 'gridDisplay("${column}$row").contains(query, ignoreCase = true)' in native
    assert 'gridDisplay("${col}$row")' in native


def test_web_filter_can_explicitly_choose_columns_through_z():
    for path in [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]:
        html = path.read_text(encoding="utf-8")
        assert "const COLS=26, ROWS=40;" in html
        assert "range=A1:Z40" in html
        # Filter column selection is now an in-app dialog (not prompt()).
        # It offers every worksheet column through Z and labels columns
        # with their first-row heading when available.
        assert "function openColumnFilterDialog()" in html
        assert "niceExcelFilterColumn" in html
        assert "for(let c=1;c<=COLS;c++)" in html
        assert "Filter worksheet column" in html
        assert ".grid td.table-cell{border-right:1px solid var(--accent);border-bottom:1px solid var(--accent)}" in html

 
def test_web_table_outer_bottom_and_right_edges_are_emphasized_without_thickening_inner_grid():
    for path in [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]:
        html = path.read_text(encoding="utf-8")
        assert ".grid td.table-cell.table-edge-right{border-right:2px solid var(--accent)!important}" in html
        assert ".grid td.table-cell.table-edge-bottom{border-bottom:2px solid var(--accent)!important}" in html
        assert "td.classList.add('table-edge-bottom')" in html
        assert "td.classList.add('table-edge-right')" in html

def test_native_black_theme_formula_bar_and_grid_headers_use_high_contrast_text():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert "setTextColor(if (darkMode) Color.WHITE else themedColor(Color.rgb(17,94,57)))" in native
    assert 'text="ƒx ▾"; textSize=12f; setTextColor(if (darkMode) Color.WHITE' in native
    assert "if (darkMode) Color.WHITE else dayPalettes" in native
