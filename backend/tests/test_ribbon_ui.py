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


def test_compact_ribbon_restores_section_tools_after_close_and_switch():
    html = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    assert "const menus=new Map(groups.map(group=>[group,group.querySelector('.toolmenu')]))" in html
    assert "if(menu)group.appendChild(menu)" in html
    assert "if(previousMenu)active.appendChild(previousMenu)" in html
    assert "if(menu)strip.appendChild(menu)" in html

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
        assert "quickFilters.push({column,mode,text:q})" in html
        assert '<option value="equal">Equal</option>' in html
        assert '<option value="not-equal">Not equal</option>' in html
        assert '<option value="blank">Blank</option>' in html
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
    assert 'matchesColumnFilter(gridDisplay("${column}$row"), query)' in native
    assert '"equal" -> value.equals(expected, ignoreCase = true)' in native
    assert '"not-equal" -> !value.equals(expected, ignoreCase = true)' in native
    assert '"blank" -> value.isBlank()' in native
    assert 'gridDisplay("${col}$row")' in native


def test_web_filter_can_explicitly_choose_columns_through_z():
    for path in [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]:
        html = path.read_text(encoding="utf-8")
        assert "let COLS=26, ROWS=40, gridExpansionBusy=false, viewSorts={};" in html
        assert "&range=A1:'+colName(COLS)+ROWS" in html
        assert "Choose the worksheet column to filter" in html
        assert "columnNumber(column)>COLS" in html
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


def test_insert_object_dialogs_are_unique_and_symbol_format_controls_are_present():
    for path in [
        ROOT / "backend" / "niceexcel_api" / "static" / "index.html",
        ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html",
    ]:
        html = path.read_text(encoding="utf-8")
        for element_id in ["shapeInsertDialog", "symbolInsertDialog"]:
            assert html.count(f'id="{element_id}"') == 1, f"duplicate dialog id {element_id} in {path}"
        for element_id in ["symbolCategory", "symbolSearch", "symbolGrid", "symbolPosition", "symbolTargetRange", "symbolFormatApply", "symbolInsertApply"]:
            assert html.count(f'id="{element_id}"') == 1, f"missing or duplicate symbol control {element_id} in {path}"
        assert "$('insertSymbolBtn').onclick" in html
        assert "formatSymbolDisplay(shownValue,numberFormat)" in html


def test_native_table_style_controls_live_in_insert_not_format():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    insert_start = native.index('"Insert" -> listOf(')
    format_start = native.index('"Format" -> listOf(', insert_start)
    insert_menu = native[insert_start:format_start]
    format_end = native.index('"Data" -> listOf(', format_start)
    format_menu = native[format_start:format_end]
    assert '"Table styles…" to { tableMenu() }' in insert_menu
    assert '"Table styles…" to { tableMenu() }' not in format_menu

def test_web_html_has_no_missing_dom_bindings_and_android_asset_matches():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    import re

    assert web == native_asset, "Web page and Android WebView asset must stay synchronized"
    element_ids = set(re.findall(r'\bid="([^"]+)"', web))
    referenced_ids = set(re.findall(r"\$\('([^']+)'\)", web))
    missing = sorted(referenced_ids - element_ids - {'sheetObjectLayer'})
    assert not missing, f"JavaScript references missing DOM elements: {missing}"
    assert "$('findBtnData').onclick=async()=>{" in web
    assert "Find text or value in this worksheet" in web
    assert "async function initializeWorkbook(){" in web
    assert "savedBookId=localStorage.getItem('niceexcel.lastWorkbookId')" in web
    assert "try{await create();}" in web
    assert web.count("initializeWorkbook();") == 1

def test_web_grid_expands_rows_and_columns_on_scroll():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    assert web == native_asset
    assert "let COLS=26, ROWS=40, gridExpansionBusy=false, viewSorts={};" in web
    assert "&range=A1:'+colName(COLS)+ROWS" in web
    assert "gridwrap.addEventListener('scroll',async()=>{" in web
    assert "ROWS=Math.min(500,ROWS+40)" in web
    assert "COLS=Math.min(52,COLS+26)" in web
    assert "Unable to extend worksheet grid:" in web
    assert "&range=A1:Z40" not in web

def test_web_add_rows_uses_single_bulk_api_request():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    assert web == native_asset
    assert "json('POST',{sheet:activeSheet,row:p?.r||1,count})" in web
    assert "for(let n=0;n<count;n++)await api('/workbooks/'+bookId+'/rows'" not in web


def test_web_grid_cell_click_focuses_cell_and_keyboard_events_do_not_reenter_editor():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    assert web == native_asset
    assert "function select(a,focus=false)" in web
    assert "td.addEventListener('click',()=>select(a,true))" in web
    assert "if(e.target!==td)return" in web
    assert "e.stopPropagation();done(true)" in web
    assert "e.stopPropagation();done(false)" in web
    assert "e.key==='Tab'&&!editing" in web
    assert "select(addr(c,r),true)" in web


def test_web_workbook_startup_has_exactly_one_initializer():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    assert web == native_asset
    assert "async function initializeWorkbook()" in web
    assert web.count("initializeWorkbook();") == 1
    assert "async function startSession()" not in web
    assert "startSession().catch(" not in web
    assert "await info();" in web
    assert "await create();" in web


def test_advanced_web_page_offers_in_app_and_same_device_browser_options():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    flask = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "FlaskActivity.kt").read_text(encoding="utf-8")
    assert '"Web Page" to { showWebPageOptions() }' in native
    assert 'arrayOf("Open inside APK", "Open in Browser (localhost)")' in native
    assert 'FlaskActivity.EXTRA_OPEN_EXTERNAL_BROWSER, true' in native
    assert 'const val EXTRA_OPEN_EXTERNAL_BROWSER' in flask
    assert 'private val localUrl = "http://127.0.0.1:8765/"' in flask
    assert 'Intent(Intent.ACTION_VIEW, Uri.parse(localUrl))' in flask
    assert 'waitForHealth(25000)' in flask


def test_web_page_uses_shared_offline_hyperformula_runtime_for_calculation_and_preview():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    runtime_html = (ROOT / "app" / "src" / "main" / "assets" / "hyperformula_runtime.html").read_text(encoding="utf-8")
    runtime_js = (ROOT / "scripts" / "hyperformula-runtime.js").read_text(encoding="utf-8")
    build_script = (ROOT / "scripts" / "build-hyperformula.mjs").read_text(encoding="utf-8")
    assert web == native_asset
    assert '<script src="/hyperformula.bundle.js"></script>' in web
    assert '<script src="/hyperformula_runtime.js"></script>' in web
    assert "async function calculateWebFormulas(" in web
    assert "'/workbooks/'+bookId+'/formula-snapshot'" in web
    assert "window.NiceExcelHF.calculate(JSON.stringify(source))" in web
    assert "window.NiceExcelHF.calculate(JSON.stringify(snapshot))" in web
    assert "formula-preview" not in web
    assert "hyperformula_runtime.js" in runtime_html
    assert "window.NiceExcelHF = {" in runtime_js
    assert "backend/niceexcel_api/static/hyperformula.bundle.js" in build_script
    assert "backend/niceexcel_api/static/hyperformula_runtime.js" in build_script


def test_web_view_sort_is_non_destructive_and_exports_sorted_projection_to_pdf():
    web = (ROOT / "backend" / "niceexcel_api" / "static" / "index.html").read_text(encoding="utf-8")
    native_asset = (ROOT / "app" / "src" / "main" / "assets" / "niceexcel_index.html").read_text(encoding="utf-8")
    assert web == native_asset
    for element_id in ["viewSortDialog", "viewSortType", "viewSortColumn", "viewSortDirection", "viewSortApply", "clearSortViewBtn"]:
        assert f'id="{element_id}"' in web
    assert '<option value="number">Number</option>' in web
    assert '<option value="letter">Letter</option>' in web
    assert "function getViewRowOrder(visibleRows" in web
    assert "compareViewSortRows(a,b,config)" in web
    assert "viewSorts[activeSheet]=" in web
    assert "delete viewSorts[activeSheet]" in web
    assert "const viewRowOrder=getViewRowOrder(visibleRows)" in web
    assert "const pdfOrder=pdfSort?getViewRowOrder(lastRow,name)" in web
    assert "await api('/workbooks/'+bookId+'/sort'" not in web
    assert "original cell positions, formulas and workbook data remain unchanged" in web


def test_native_sort_is_view_only_and_pdf_uses_sorted_row_projection():
    native = (ROOT / "app" / "src" / "main" / "java" / "com" / "meraj" / "niceexcel" / "MainActivity.kt").read_text(encoding="utf-8")
    assert '"Sort View…" to { sortColumn() }' in native
    assert '"Clear Sort View" to { clearSortView() }' in native
    assert "private data class ViewSortConfig" in native
    assert "private fun viewSortOrder(" in native
    assert "private fun applyViewSortOrderToGrid()" in native
    assert "viewSortConfigs[sheetName] = ViewSortConfig(column, type, descending, headerRow)" in native
    assert "data.remove(address)" not in native[native.index("private fun sortColumn() {"):native.index("override fun onDestroy()", native.index("private fun sortColumn() {"))]
    assert "val pdfSortSnapshot = viewSortConfigs.toMap()" in native
    assert "val orderedRows = viewSortOrder(name, maxRow, cells, pdfSortSnapshot[name], stMap, formulaSnapshot)" in native
    assert "val sourceRow = orderedRows.getOrNull(r - 1) ?: r" in native

