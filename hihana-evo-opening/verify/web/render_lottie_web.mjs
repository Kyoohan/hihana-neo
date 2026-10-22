#!/usr/bin/env node
// Render every frame of a Lottie JSON with lottie-web's canvas renderer in headless Chromium.
//
//   node render_lottie_web.mjs <json> <outDir> [--bg #0B0B1A] [--scale 1] [--start N] [--end N]
//                              [--build lottie_canvas] [--chromium /path/to/chrome]
//
// Output: <outDir>/frame_NNNNN.png, NNNNN = absolute composition frame (ip-based), same naming as the
// lottie-android harness. Frames ip .. op-1 by default (lottie-web's totalFrames).
// --bg "transparent" keeps alpha. --chromium (or env CHROMIUM_PATH) overrides the browser binary if the
// pinned playwright ever wants a revision that isn't installed under $PLAYWRIGHT_BROWSERS_PATH.
import { chromium } from 'playwright';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);

function parseArgs(argv) {
  const pos = [];
  const opt = { bg: '#0B0B1A', scale: 1, start: null, end: null, build: 'lottie_canvas', chromium: process.env.CHROMIUM_PATH || null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const k = a.slice(2);
      const v = argv[++i];
      if (!(k in opt)) throw new Error(`unknown option ${a}`);
      opt[k] = ['scale', 'start', 'end'].includes(k) ? Number(v) : v;
    } else pos.push(a);
  }
  if (pos.length < 2) {
    console.error('usage: node render_lottie_web.mjs <json> <outDir> [--bg #0B0B1A|transparent] [--scale 1] [--start N] [--end N]');
    process.exit(2);
  }
  return { json: path.resolve(pos[0]), outDir: path.resolve(pos[1]), ...opt };
}

const args = parseArgs(process.argv.slice(2));
const animationData = JSON.parse(fs.readFileSync(args.json, 'utf8'));
const lottieJs = require.resolve(`lottie-web/build/player/${args.build}.min.js`);
const lottieVersion = JSON.parse(fs.readFileSync(require.resolve('lottie-web/package.json'), 'utf8')).version;
fs.mkdirSync(args.outDir, { recursive: true });

const W = Math.round(animationData.w * args.scale);
const H = Math.round(animationData.h * args.scale);

const browser = await chromium.launch({ executablePath: args.chromium || undefined });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
page.on('console', (m) => console.log(`[browser:${m.type()}] ${m.text()}`));
page.on('pageerror', (e) => console.log(`[browser:pageerror] ${e.message}`));
await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent">
  <div id="c" style="width:${W}px;height:${H}px"></div></body></html>`);
await page.addScriptTag({ path: lottieJs });

const info = await page.evaluate(({ data, W, H, bg }) => {
  const container = document.getElementById('c');
  const anim = window.lottie.loadAnimation({
    container, renderer: 'canvas', loop: false, autoplay: false, animationData: data,
    rendererSettings: { clearCanvas: true, dpr: 1, preserveAspectRatio: 'xMidYMid meet' },
  });
  const out = document.createElement('canvas');
  out.width = W; out.height = H;
  const octx = out.getContext('2d');
  window.__render = (relFrame) => {
    anim.goToAndStop(relFrame, true);
    const src = container.querySelector('canvas');
    octx.clearRect(0, 0, W, H);
    if (bg !== 'transparent') { octx.fillStyle = bg; octx.fillRect(0, 0, W, H); }
    octx.drawImage(src, 0, 0, W, H);
    return out.toDataURL('image/png');
  };
  const src = container.querySelector('canvas');
  return {
    version: window.lottie.version, firstFrame: anim.firstFrame, totalFrames: anim.totalFrames,
    frameRate: anim.frameRate, canvas: `${src.width}x${src.height}`,
    markers: (data.markers || []).map((m) => ({ name: m.cm, startFrame: m.tm, durationFrames: m.dr })),
  };
}, { data: animationData, W, H, bg: args.bg });

const browserVersion = browser.version();
console.log(`[lottie-web] lottie-web ${lottieVersion} (${args.build}) chromium ${browserVersion}`);
console.log(`[lottie-web] ${args.json}: ${animationData.w}x${animationData.h} -> ${W}x${H}, fps=${info.frameRate}, ip=${info.firstFrame}, totalFrames=${info.totalFrames}, lottie canvas=${info.canvas}`);
for (const m of info.markers) console.log(`[lottie-web-marker] name=${m.name} startFrame=${m.startFrame} durationFrames=${m.durationFrames}`);

const first = args.start ?? info.firstFrame;
const last = args.end ?? info.firstFrame + info.totalFrames - 1;
const t0 = performance.now();
const writes = [];
for (let f = first; f <= last; f++) {
  const dataUrl = await page.evaluate((rel) => window.__render(rel), f - info.firstFrame);
  const buf = Buffer.from(dataUrl.slice(dataUrl.indexOf(',') + 1), 'base64');
  writes.push(fs.promises.writeFile(path.join(args.outDir, `frame_${String(f).padStart(5, '0')}.png`), buf));
}
await Promise.all(writes);
const ms = performance.now() - t0;
const n = last - first + 1;
console.log(`[lottie-web] rendered ${n} frames (${first}..${last}) into ${args.outDir} in ${(ms / 1000).toFixed(1)} s (${(ms / n).toFixed(1)} ms/frame)`);
await browser.close();
