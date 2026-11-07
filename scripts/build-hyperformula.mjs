import { build } from 'esbuild';
import { copyFile, mkdir } from 'node:fs/promises';

const outputs = [
  'app/src/main/assets/hyperformula.bundle.js',
  'backend/niceexcel_api/static/hyperformula.bundle.js',
];
for (const outfile of outputs) {
  await mkdir(outfile.slice(0, outfile.lastIndexOf('/')), { recursive: true });
  await build({
    entryPoints: ['scripts/hyperformula-entry.js'],
    outfile,
    bundle: true,
    minify: true,
    platform: 'browser',
    format: 'iife',
    target: ['es2020'],
    legalComments: 'inline',
    sourcemap: false,
  });
}
const runtimeTargets = [
  'app/src/main/assets/hyperformula_runtime.js',
  'backend/niceexcel_api/static/hyperformula_runtime.js',
];
for (const target of runtimeTargets) {
  await mkdir(target.slice(0, target.lastIndexOf('/')), { recursive: true });
  await copyFile('scripts/hyperformula-runtime.js', target);
}
const pdfTargets = [
  'app/src/main/assets/niceexcel_pdf.bundle.js',
  'backend/niceexcel_api/static/niceexcel_pdf.bundle.js',
];
for (const outfile of pdfTargets) {
  await mkdir(outfile.slice(0, outfile.lastIndexOf('/')), { recursive: true });
  await build({
    entryPoints: ['scripts/niceexcel-pdf-entry.js'],
    outfile,
    bundle: true,
    minify: true,
    platform: 'browser',
    format: 'iife',
    target: ['es2020'],
    legalComments: 'none',
    sourcemap: false,
  });
}
console.log('Bundled offline HyperFormula and PDF runtimes for Web Page and Android WebView.');
