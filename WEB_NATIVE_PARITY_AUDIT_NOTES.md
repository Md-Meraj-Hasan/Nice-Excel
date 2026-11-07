# NiceExcel Web vs Native APK — Feature Parity Audit Notes

**Audit date:** 2026-11-06  
**Branch audited:** `feature/starts-with-filter-option5-icon`  
**Scope:** Read-only source audit. Native APK source and behaviour are protected; this audit makes no Native code changes.

## Executive summary

The Web page is not a thin wrapper around the Native APK. It has its own HTML/JavaScript grid, event handlers, API calls and a separate Python backend. Native behaviour is implemented mainly in `MainActivity.kt` plus the Android-side Python API. As a result, a feature being present in Native does not automatically make it work on Web.

The audit found:
1. The Web page already contains a large set of tools, but the UI presence alone does not prove the full operation is implemented or equivalent to Native.
2. The Native Android activity contains several dedicated implementations that have no matching function/name in the Web page source, including native shape insertion/options, symbol picker, row actions, formula picker, formula help, autofill, merge/unmerge dialogs, native currency/custom number format dialogs, table-style actions, data validation/cleaning menus, pivot summary, colour filtering, and native PDF/CSV workflows. Some Web equivalents may exist under different names or be partial; each needs behaviour-level verification.
3. The production Web backend has endpoints for Smart Tables and reporting metadata. The Android-embedded Python API does not currently expose those same endpoints. This is a concrete backend parity gap.
4. Web and Android use separate Python app entry points, even though some shared modules are byte-identical. A fix in one backend must not be assumed to reach the other.
5. The recent Web Open button change only addresses file-picker invocation. It does not solve the broader feature-parity problem.

## Source files examined

- `backend/niceexcel_api/static/index.html` — Web UI, grid renderer, toolbar/ribbon, formula bar, event handlers.
- `backend/niceexcel_api/main.py` — Web backend API routes and workbook persistence.
- `backend/niceexcel_api/formula_engine.py` — Web-side formula engine.
- `backend/niceexcel_api/conditional_formatting.py` — shared conditional-formatting implementation.
- `app/src/main/java/com/meraj/niceexcel/MainActivity.kt` — Native Android UI and feature implementations.
- `app/src/main/java/com/meraj/niceexcel/HyperFormulaServiceClient.kt` — Native formula service bridge.
- `app/src/main/python/niceexcel_api/main.py` — Android-embedded Python API.
- `app/src/main/python/niceexcel_api/formula_engine.py` — Android-side formula engine.

## Confirmed backend route differences

The Web backend exposes these routes which are absent from the Android-embedded Python API:

| Route | Web backend | Android embedded API | Impact |
|---|---|---|---|
| `GET /workbooks/{book_id}/reporting/metadata` | Present | Missing | Reporting metadata cannot be loaded through the embedded API contract |
| `PUT /workbooks/{book_id}/reporting/metadata` | Present | Missing | Reporting metadata cannot be saved through the embedded API contract |
| `POST /workbooks/{book_id}/tables` | Present | Missing | Smart Table creation API is not available in embedded API |

This is a real API contract mismatch. It does not necessarily mean Native's own table UI is broken; Native has a dedicated table implementation in `MainActivity.kt`. It does mean the two backends are not feature-equivalent.

Both backends expose core workbook operations such as open, workbook read, range read, rows, sheets, merge, find/replace, sort, validation, conditional formatting, formula preview, retrieve-data, undo/redo, history batch, dependencies, calculate, save and CSV operations. Their implementations still need contract-level tests, not just route-name matching.

## Native features requiring Web behaviour-level parity checks

The following Native implementations were identified in `MainActivity.kt`. The Web UI has some similarly named tools, but no direct matching Native method names in its JavaScript. These are audit targets, not a claim that every Web equivalent is entirely absent.

| Native implementation | Web parity status from source inspection | Required Web verification |
|---|---|---|
| `showNativeShapesMenu`, `insertNativeShape`, `showNativeShapeOptions` | No matching Web shape workflow found by name | Insert, select, move, resize, edit options, delete, persist/reopen shapes |
| `showSymbolPickerMenu` | No matching Web symbol picker found by name | Symbol catalogue, insert at caret/cell, Unicode persistence |
| `addRows`, `addOneRow`, `duplicateSelectedRowToBottom`, `clearSelectedRowConfirm` | Web exposes “＋ 10+ Rows”; complete parity unconfirmed | Add one/multiple rows, duplicate, clear, selection and formula/range shifts |
| `formulaPickerMenu`, `formulaHelp`, `applyFormula` | Web has a formula picker/list; parity unconfirmed | Search/list, insert formula, argument hints, formula-bar editing, calculation |
| `autoFillDialog`, formula translation helpers | No direct matching Web autofill dialog found | Drag/fill series, relative/absolute references, formula translation |
| `mergeCellsDialog`, `unmergeCellsDialog` | Web merge API exists; complete UI parity unconfirmed | Merge/unmerge, preserve top-left value, selection/rendering, save/reopen |
| `chooseCurrencyFormat`, `customNumberFormatDialog` | Web has number-format controls; currency selection/custom range parity unconfirmed | Currency symbol selection, decimals, prefix/suffix, range targeting, formula value remains numeric |
| `tableMenu`, `chooseNativeTableStyle`, `applyTableRangeAction` | Web has Smart Table dialog and backend endpoint | Style catalogue, table range, header/totals, filters, persistence and editability |
| `conditionalFormattingMenu`, `applyConditionalRule` | Web has conditional-formatting UI and backend endpoints | Rule types, range application, precedence, edit/delete, save/reopen |
| `dataValidationMenu` | Web has Data Validation tool and endpoints | List/number/date rules, reject/warn, dropdown, persistence |
| `dataCleaningMenu` | Web has Data Cleaning tool | Every cleaning action, preview/confirm, undo, save/reopen |
| `pivotSummaryMenu` | Web exposes Pivot Table and Dashboard Builder | Actual aggregation, grouping, refresh, source changes and persistence |
| `filterByCellColour`, advanced filter methods | Web exposes Filter by Color and filter controls | Filter by fill/font colour, multiple criteria, clear, visible-row formula calculations |
| `savePdfMenu`, `saveWorkbookPdf`, `saveCsv` | Web has PDF/CSV export controls | Export options, selected sheet/range, sheet-name toggle, repeat header, file correctness |

## Web-side implementation observations

The Web page has its own large client-side implementation in `backend/niceexcel_api/static/index.html`, including:
- Grid rendering, selection, paste, inline editing, formula-bar sync and formula preview.
- Filter controls, smart-table filter UI, sorting, range parsing and selected-colour filtering.
- Conditional formatting, data validation, data cleaning and pivot/dashboard entry points.
- Theme palette, symbol grid renderer, object overlay renderer and compact ribbon setup.
- Workbook open/save, PDF/CSV export, undo/redo and recovery-related UI.

Important: a helper or toolbar label being present is not proof that its full action works. Each item needs an interaction test against a real workbook.

## High-priority issues to verify first

1. **Web Open and workbook lifecycle:** select XLSX/XLSM/CSV, load all sheets, preserve formulas/styles/widths/merged cells, switch sheets, save and reopen.
2. **Formula calculation parity:** test ordinary formulas and formula dependencies first, then SUBTOTAL/filtered rows, array/spill formulas, lookup formulas, error values and save/reopen.
3. **Filter visibility contract:** visible row indices must be consistent between Web grid, backend calculation, sort, subtotal and export. Filtered rows must not be accidentally included in filter-aware calculations.
4. **Smart Table parity:** Web endpoint exists; Android embedded endpoint is missing. Keep this as a separate backend parity decision, not a Native code change.
5. **Number formats:** verify currency symbol choice (not fixed dollar), prefix/suffix on a selected range, numeric underlying values, formulas and persistence.
6. **Range targeting:** every formatting/data tool must apply to selected cell or selected range exactly, not accidentally to a single cell or the whole sheet.
7. **Undo/redo and autosave:** ensure Web operations are recorded in history and that reopening the workbook preserves the final state.
8. **PDF/CSV export:** verify export output itself, not just that the dialog opens.

## Regression test matrix to build for Web

| Area | Test workbook/action | Expected result |
|---|---|---|
| Open | XLSX with 3 sheets, formulas, styles, merged cells | Opens with all sheet content and formatting intact |
| Save/reopen | Edit a value and formula, save, reopen | Values, formulas, formatting and sheet names persist |
| Formula | `=SUM(C3,C4)`, `=AVERAGE(C3:C20)`, `=SUBTOTAL(9,C9:C33)` | Numeric results, never placeholder text |
| Filter | Filter a range, then SUBTOTAL 1–11 and 101–111 | Only rows matching each function's visibility rules are counted |
| Formula display | Select formula cell | Formula bar shows original formula; grid shows calculated value |
| Number format | Currency symbol options, prefix/suffix applied to range | Display changes; underlying numeric values and formulas remain numeric |
| Rows/sheets | Add rows, add sheet, rename/delete sheet | Grid and workbook structure remain consistent after reopen |
| Merge | Merge and unmerge a range | Correct layout and values; no hidden cells or shifted data |
| Smart Table | Create table with header and totals row | Correct range, style, filter and persisted definition |
| Conditional formatting | Add/edit/remove rule on range | Correct cells render; rule survives reopen |
| Data validation | Add list validation and enter valid/invalid values | Dropdown/validation behaviour is correct and persisted |
| Data cleaning | Run each cleaning action on selected range | Only intended cells change; undo restores prior data |
| Pivot/dashboard | Build summary from a source range | Correct aggregation; refresh after source edit |
| Shapes/symbols | Insert, edit, save/reopen | Objects/symbols remain visible and editable |
| PDF/CSV | Export selected sheet/range | Correct data, headings, sheet-name option and formatting |
| Undo/redo | Run several mixed operations | State returns exactly; no long freeze or lost changes |

## Safe implementation plan — Web only

1. Freeze Native scope: do not edit `app/src/main/java/**` or `app/src/main/python/**` while fixing Web parity.
2. Establish a Web baseline with a workbook fixture and capture current failures before changing code.
3. Fix Web-only UI handlers and Web backend contracts in `backend/niceexcel_api/static/index.html` and `backend/niceexcel_api/**`.
4. Add automated Web API tests and browser-level interaction tests for each feature group.
5. Run existing backend tests and Web regression tests after each small patch.
6. Compare saved XLSX output with the source workbook after open/edit/save/reopen.
7. Only mark a feature “matched” after its interaction and persistence tests pass.

## Explicit non-goals for this audit

- No Native APK source edits.
- No refactor of shared formula engine or shared modules.
- No UI redesign.
- No feature removal or replacement.
- No claim that Web parity is complete based only on matching button names or API route names.

## Current conclusion

The Web page has a substantial toolset, but it is an independent implementation and currently lacks proven parity with the Native implementation. The most concrete backend gap is the missing embedded-API Smart Table and reporting metadata routes; the most important client-side audit targets are shapes, symbols, row actions, autofill, formula tools, formatting, validation/cleaning, pivot/dashboard, filters, and export. Proceed with a Web-only test-first parity pass, preserving Native files untouched.
