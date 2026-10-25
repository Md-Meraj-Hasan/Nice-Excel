# Nice Excel — Android Native Kotlin

Offline-first starter project for a phone-friendly spreadsheet app.

Included: native Kotlin UI (no WebView), 12-column x 40-row editable grid, formula bar, basic arithmetic (`=A1+B1`, `=B2*C2`, parentheses), range functions (`SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`), CSV open/save through Android document picker, no Internet permission.

Not yet included: XLSX import/export, full Excel formula compatibility, charts, multiple worksheets, formatting, sorting/filtering, undo/redo, large-sheet virtualization.

Build with Android Studio. `minSdk 24`, `compileSdk 35`, `targetSdk 35`. Treat this as a starter version; build and test on devices before release.
