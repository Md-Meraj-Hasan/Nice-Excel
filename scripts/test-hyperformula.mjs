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
hf.destroy();
console.log('HyperFormula GPLv3 smoke tests passed: SUM, cross-sheet references, VLOOKUP, XLOOKUP, FILTER array cells, booleans, and #DIV/0!.');
