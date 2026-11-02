import assert from 'node:assert/strict';
import { HyperFormula } from 'hyperformula';
import { normalizeFormula } from '../app/src/main/assets/hyperformula-compat.js';

const workbook = HyperFormula.buildFromSheets({
  Data: [
    ['ID', 'Item', 'Qty'],
    [102, 'Pen', 3],
    [105, 'Notebook', 2],
    [108, 'Ruler', 4],
    [105, 'Notebook 2', 5],
  ],
  Calc: [[
    '=SUM(Data!C2:C5)',
    normalizeFormula('=VLOOKUP(105,Data!A2:C5,2,FALSE)'),
    '=XLOOKUP(108,Data!A2:A4,Data!B2:B4,"Not Found")',
    '=Data!B3',
    '=1/0',
    '=FILTER(Data!A2:A5,Data!A2:A5=105)',
  ]],
}, { licenseKey: 'gpl-v3' });

const sheet = workbook.getSheetId('Calc');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 0 }), 9);
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 1 }), 'Notebook');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 2 }), 'Ruler');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 3 }), 'Notebook');
const divideByZero = workbook.getCellValue({ sheet, row: 0, col: 4 });
assert.equal(divideByZero.value, '#DIV/0!');
assert.equal(normalizeFormula('=SUM(A1,\"FALSE\")'), '=SUM(A1,\"FALSE\")');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 5 }), 105);
assert.equal(workbook.getCellValue({ sheet, row: 1, col: 5 }), 105);

workbook.setCellContents({ sheet: workbook.getSheetId('Data'), row: 1, col: 2 }, [[10]]);
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 0 }), 21);

workbook.destroy();
process.stdout.write('HyperFormula regression checks passed.\n');
