import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';

const outfile = 'app/src/main/assets/hyperformula.bundle.js';
await mkdir('app/src/main/assets', { recursive: true });
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
console.log(`Bundled offline HyperFormula runtime: ${outfile}`);
