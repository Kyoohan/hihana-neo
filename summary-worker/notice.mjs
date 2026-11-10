#!/usr/bin/env node
/*
 * 앱 공지 팝업 올리기·내리기 — 워커 KV(SUMMARIES)의 notice:current 한 건. 앱(RemoteNotice.kt)은 실행·복귀 때(15분에 한 번까지)
 * /notice 를 읽어 처음 보는 공지면 한 번 띄우고, 닫으면 그 공지는 다시 띄우지 않습니다. 새로 올리면 새 공지라 다시 뜹니다.
 *
 *   node notice.mjs "제목" "본문"                          모두에게
 *   node notice.mjs "제목" "본문" --to dev                 dev 빌드에만 (먼저 내 폰에서 확인할 때)
 *   node notice.mjs "제목" "본문" --to public              일반 빌드에만
 *   node notice.mjs "제목" "본문" --link https://… --button "열기"
 *   node notice.mjs "제목" "본문" --until 2026-11-20       그날이 지나면 안 띄움
 *   node notice.mjs --show                                  지금 걸린 공지 보기
 *   node notice.mjs --clear                                 내리기
 *
 * 본문 줄바꿈은 \n 으로 적습니다. 워커 배포(/notice 길)가 한 번 돼 있어야 앱이 읽습니다.
 */
import { spawnSync } from "node:child_process";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const KEY = "notice:current";
const cwd = dirname(fileURLToPath(import.meta.url));

function wrangler(...args) {
  const r = spawnSync("npx", ["wrangler", "kv", "key", ...args, "--binding", "SUMMARIES", "--remote"], { cwd, stdio: "inherit" });
  process.exit(r.status ?? 1);
}

function fail(message) {
  console.error(message);
  process.exit(2);
}

const argv = process.argv.slice(2);
if (argv[0] === "--show") wrangler("get", KEY, "--text");
if (argv[0] === "--clear") wrangler("delete", KEY);

const positional = [];
const opts = {};
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a.startsWith("--")) {
    if (i + 1 >= argv.length) fail(`${a} 뒤에 값이 없습니다`);
    opts[a.slice(2)] = argv[++i];
  } else positional.push(a);
}
const [title, body = ""] = positional;
if (!title) fail('사용법: node notice.mjs "제목" "본문" [--to dev|public] [--link URL --button 글자] [--until YYYY-MM-DD]\n       node notice.mjs --show | --clear');
const unknown = Object.keys(opts).filter((k) => !["to", "link", "button", "until"].includes(k));
if (unknown.length) fail(`모르는 옵션: ${unknown.map((k) => "--" + k).join(" ")}`);
if (opts.to && !["dev", "public"].includes(opts.to)) fail("--to 는 dev, public 중 하나");
if (opts.link && !/^https:\/\//.test(opts.link)) fail("--link 는 https:// 로 시작해야 합니다");
if (opts.button && !opts.link) fail("--button 은 --link 와 함께");
if (opts.until && !/^\d{4}-\d{2}-\d{2}$/.test(opts.until)) fail("--until 은 YYYY-MM-DD");

const notice = {
  // 올린 시각이 곧 id — 같은 글을 다시 올려도 새 공지로 한 번 더 뜹니다.
  id: new Date().toISOString().replace(/\.\d+Z$/, "Z"),
  title,
  body: body.replace(/\\n/g, "\n"),
  ...(opts.to ? { to: opts.to } : {}),
  ...(opts.link ? { link: opts.link, button: opts.button || "열기" } : {}),
  ...(opts.until ? { until: opts.until } : {}),
};
console.log(JSON.stringify(notice, null, 2));
wrangler("put", KEY, JSON.stringify(notice));
