import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = process.cwd();
const targets = [
  resolve(root, 'app/src/main/assets/hyperformula.bundle.js'),
  resolve(root, 'backend/niceexcel_api/static/hyperformula.bundle.js'),
];
for (const target of targets) {
  await mkdir(resolve(target, '..'), { recursive: true });
  await build({
    entryPoints: [resolve(root, 'scripts/hyperformula-entry.js')],
    outfile: target,
    bundle: true,
    minify: true,
    platform: 'browser',
    format: 'iife',
    target: ['es2020'],
    legalComments: 'eof',
  });
}
// HyperFormula is GPL-3.0-only; the repository's LICENSE is the complete matching GPLv3 text.
const sourceLicense = resolve(root, 'LICENSE');
const licenseTargets = [
  resolve(root, 'app/src/main/assets/THIRD_PARTY_LICENSES/HyperFormula-GPL-3.0.txt'),
  resolve(root, 'backend/niceexcel_api/static/hyperformula-license.txt'),
];
for (const licenseTarget of licenseTargets) {
  await mkdir(resolve(licenseTarget, '..'), { recursive: true });
  await copyFile(sourceLicense, licenseTarget);
}
console.log('HyperFormula offline bundles and GPL-3.0 license notices generated.');
