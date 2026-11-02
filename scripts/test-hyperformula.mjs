import assert from 'node:assert/strict';
import { HyperFormula } from 'hyperformula';

const workbook = HyperFormula.buildFromSheets({
  Data: [
    ['ID', 'Item', 'Qty'],
    [102, 'Pen', 3],
    [105, 'Notebook', 2],
    [108, 'Ruler', 4],
  ],
  Calc: [[
    '=SUM(Data!C2:C4)',
    '=VLOOKUP(105,Data!A2:C4,2,0)',
    '=XLOOKUP(108,Data!A2:A4,Data!B2:B4,"Not Found")',
    '=Data!B3',
    '=1/0',
  ]],
}, { licenseKey: 'gpl-v3' });

const sheet = workbook.getSheetId('Calc');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 0 }), 9);
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 1 }), 'Notebook');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 2 }), 'Ruler');
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 3 }), 'Notebook');
const divideByZero = workbook.getCellValue({ sheet, row: 0, col: 4 });
assert.equal(divideByZero.value, '#DIV/0!');

workbook.setCellContents({ sheet: workbook.getSheetId('Data'), row: 1, col: 2 }, [[10]]);
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 0 }), 16);

workbook.destroy();
process.stdout.write('HyperFormula regression checks passed.\n');
