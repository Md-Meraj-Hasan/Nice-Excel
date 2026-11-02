import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { HyperFormula } from 'hyperformula';
import { normalizeFormula } from '../app/src/main/assets/hyperformula-compat.js';

const htmlFiles = [
  'app/src/main/assets/hyperformula-runtime.html',
  'app/src/main/assets/niceexcel_index.html',
  'backend/niceexcel_api/static/index.html',
];
for (const file of htmlFiles) {
  const html = await readFile(resolve(process.cwd(), file), 'utf8');
  const inlineScripts = [...html.matchAll(/<script(?:\\s[^>]*)?>([\\s\\S]*?)<\\/script>/g)]
    .map(match => match[1]).filter(script => script.trim().length > 0);
  assert.ok(inlineScripts.length > 0, `No inline JavaScript found in ${file}`);
  for (const script of inlineScripts) new Function(script);
}

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
assert.equal(workbook.getCellValue({ sheet, row: 0, col: 0 }), 14);
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

const sparse = HyperFormula.buildEmpty({ licenseKey: 'gpl-v3', useColumnIndex: true });
const sparseName = sparse.addSheet('Sparse');
const sparseSheet = sparse.getSheetId(sparseName);
sparse.setCellContents({ sheet: sparseSheet, row: 999, col: 25 }, [[7]]);
assert.equal(sparse.getCellValue({ sheet: sparseSheet, row: 999, col: 25 }), 7);
sparse.destroy();

workbook.destroy();
process.stdout.write('HyperFormula regression checks passed.\n');
