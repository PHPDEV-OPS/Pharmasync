// Bundles src/index.ts into dist/index.mjs and packages dist/function.zip for Neon Functions.
import { build } from 'esbuild';
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync } from 'node:fs';

rmSync('dist', { recursive: true, force: true });
mkdirSync('dist');

await build({
  entryPoints: ['src/index.ts'],
  bundle: true,
  platform: 'node',
  target: 'node24',
  format: 'esm',
  outfile: 'dist/index.mjs',
  minify: true,
  legalComments: 'none',
  // pg's optional native bindings aren't used.
  external: ['pg-native'],
  // Restores require/__dirname for bundled CommonJS dependencies such as pg.
  banner: {
    js: "import{createRequire as ___cr}from'module';import{fileURLToPath as ___f}from'url';import{dirname as ___d}from'path';const require=___cr(import.meta.url);const __filename=___f(import.meta.url);const __dirname=___d(__filename);",
  },
});

execFileSync('python', ['-c', "import zipfile;z=zipfile.ZipFile('dist/function.zip','w',zipfile.ZIP_DEFLATED);z.write('dist/index.mjs','index.mjs');z.close()"]);
console.log('Built dist/function.zip');
