import { HyperFormula } from 'hyperformula';
import { normalizeFormula } from '../app/src/main/assets/hyperformula-compat.js';

// Expose the bundled engine to the offline browser and Android WebView runtime.
globalThis.HyperFormula = HyperFormula;
globalThis.NiceExcelFormulaCompat = { normalizeFormula };
