import assert from 'node:assert/strict';
import { HyperFormula } from 'hyperformula';

const hf = HyperFormula.buildFromSheets({
  Sheet1: [
    ['ID', 'Item', 'Price'],
    [101, 'Pen', 2],
    [105, 'Notebook', 5],
    [108, 'Pencil', 3],
  ],
  Sheet2: [
    ['=SUM(Sheet1!C2:C4)'],
    ['=VLOOKUP(105,Sheet1!A2:C4,2,FALSE())'],
    ['=XLOOKUP(108,Sheet1!A2:A4,Sheet1!B2:B4,"Not Found")'],
    ['=1/0'],
    ['=TRUE()'],
    ['', '', '', '', '=FILTER(Sheet1!B2:B4,Sheet1!C2:C4>2)'],
  ],
}, { licenseKey: 'gpl-v3' });
const value = (row) => hf.getCellValue({ sheet: hf.getSheetId('Sheet2'), row, col: 0 });
assert.equal(value(0), 10, 'cross-sheet SUM');
assert.equal(value(1), 'Notebook', 'VLOOKUP exact match');
assert.equal(value(2), 'Pencil', 'XLOOKUP exact match');
assert.equal(value(3)?.value, '#DIV/0!', 'Excel error propagation');
assert.equal(value(4), true, 'boolean function value');
const filterAnchor = { sheet: hf.getSheetId('Sheet2'), row: 5, col: 4 };
const filterSpill = { sheet: hf.getSheetId('Sheet2'), row: 6, col: 4 };
assert.equal(hf.getCellValue(filterAnchor), 'Notebook', 'FILTER first result');
assert.equal(hf.isCellPartOfArray(filterSpill), true, 'FILTER result cell belongs to array');
assert.equal(hf.getCellValue(filterSpill), 'Pencil', 'FILTER second result');
const dimensions = hf.getSheetDimensions(filterAnchor.sheet);
const values = hf.getAllSheetsValues().Sheet2;
const formulas = hf.getAllSheetsFormulas().Sheet2;
const spillCells = {};
for (let row = 0; row < formulas.length && row < 10000; row++) {
  for (let col = 0; col < (formulas[row] || []).length && col < 512; col++) {
    if (!formulas[row][col]) continue;
    const anchor = { sheet: filterAnchor.sheet, row, col };
    if (!hf.isCellPartOfArray(anchor)) continue;
    let height = 1, width = 1;
    while (row + height < dimensions.height && hf.isCellPartOfArray({ sheet: filterAnchor.sheet, row: row + height, col })) height++;
    while (col + width < dimensions.width && hf.isCellPartOfArray({ sheet: filterAnchor.sheet, row, col: col + width })) width++;
    for (let spillRow = row; spillRow < row + height; spillRow++) {
      for (let spillCol = col; spillCol < col + width; spillCol++) {
        if (spillRow === row && spillCol === col) continue;
        let n = spillCol + 1, letters = '';
        while (n > 0) { const rem = (n - 1) % 26; letters = String.fromCharCode(65 + rem) + letters; n = Math.floor((n - 1) / 26); }
        spillCells['Sheet2!' + letters + (spillRow + 1)] = values[spillRow]?.[spillCol];
      }
    }
  }
}
assert.equal(spillCells['Sheet2!E7'], 'Pencil', 'native array spill extraction');
// Reproduce the exact COUNTIF and MATCH formulas visible in the user's
// Formula Tests workbook, including absolute references and helper columns J/M.
const compatibilityRows = Array.from({ length: 14 }, () => Array(13).fill(null));
[10, 20, 30, 40, 50, 60].forEach((value, index) => { compatibilityRows[index + 1][9] = value; });
['P01', 'P02', 'P03', 'P04', 'P05'].forEach((value, index) => { compatibilityRows[index + 1][12] = value; });
compatibilityRows[1][3] = '=COUNTIF($J$2:$J$7,">10")';
compatibilityRows[13][3] = '=MATCH("P04",$M$2:$M$6,0)';
const compatibility = HyperFormula.buildFromSheets(
  { 'Formula Tests': compatibilityRows },
  { licenseKey: 'gpl-v3' }
);
const testSheet = compatibility.getSheetId('Formula Tests');
assert.equal(compatibility.getCellValue({ sheet: testSheet, row: 1, col: 3 }), 5, 'exact COUNTIF absolute-range test');
assert.equal(compatibility.getCellValue({ sheet: testSheet, row: 13, col: 3 }), 4, 'exact MATCH absolute-range test');
// Regression coverage for the exact boolean and lookup formulas reported
// by the Android FORMULA_VALUE diagnostics. Keep HLOOKUP and VLOOKUP in
// separate sheets because the user's two sample ranges overlap at M2:N4.
const regression = HyperFormula.buildFromSheets({
  BooleanTests: [
    ['=AND(TRUE,TRUE,1=1)', '=OR(FALSE,FALSE,2=2)', '=NOT(FALSE)'],
    ['=AND(TRUE(),TRUE(),1=1)', '=OR(FALSE(),FALSE(),2=2)', '=NOT(FALSE())'],
    ['=TRUE', '=FALSE', '=TRUE()'],
  ],
  HLookupTests: [
    [],
    [],
    [],
  ],
  VLookupTests: [
    [],
    [],
    [],
    [],
  ],
}, { licenseKey: 'gpl-v3' });
const boolSheet = regression.getSheetId('BooleanTests');
const boolValue = (row, col) => regression.getCellValue({ sheet: boolSheet, row, col });
const regressionResults = {
  andBareTrue: boolValue(0, 0),
  orBareFalse: boolValue(0, 1),
  notBareFalse: boolValue(0, 2),
  andFunctionControl: boolValue(1, 0),
  orFunctionControl: boolValue(1, 1),
  notFunctionControl: boolValue(1, 2),
  bareTrue: boolValue(2, 0),
  bareFalse: boolValue(2, 1),
  trueFunctionControl: boolValue(2, 2),
  hlookupBareFalse: regression.getCellValue({ sheet: hSheet, row: 0, col: 0 }),
  vlookupBareFalse: regression.getCellValue({ sheet: vSheet, row: 0, col: 0 }),
};
console.log('Boolean/lookup regression results:', JSON.stringify(regressionResults));
const expectedRegressionResults = {
  andBareTrue: true, orBareFalse: true, notBareFalse: true,
  andFunctionControl: true, orFunctionControl: true, notFunctionControl: true,
  bareTrue: true, bareFalse: false, trueFunctionControl: true,
  hlookupBareFalse: 200, vlookupBareFalse: 200,
};
assert.deepEqual(regressionResults, expectedRegressionResults, 'boolean and lookup formula compatibility');
regression.destroy();
compatibility.destroy();
hf.destroy();
console.log('HyperFormula GPLv3 smoke tests passed: SUM, cross-sheet references, VLOOKUP, XLOOKUP, FILTER array cells, booleans, COUNTIF, MATCH, and #DIV/0!. SUM, cross-sheet references, VLOOKUP, XLOOKUP, FILTER array cells, booleans, and #DIV/0!.');
