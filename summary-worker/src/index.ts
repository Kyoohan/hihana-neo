/**
 * 하이하나 네오 게시글 요약 서버.
 *
 * POST /summarize { title, text, images? } → { line, points, cached }
 *   images : 본문 이미지 주소(포털 업로드 파일만). 가정통신문처럼 본문이 이미지뿐인 글은 이미지 속 글자를
 *            먼저 읽어 낸 뒤 본문과 합쳐 요약합니다.
 *   line   : 게시판 목록에 붙는 한 줄 요약
 *   points : 글 화면 위에 보이는 자세한 요약(글머리표)
 *
 * 키 앞의 v3 는 프롬프트 버전 — 프롬프트를 바꾸면 올려서 예전 요약 대신 새로 만들게 합니다.
 * 요약은 (제목 + 본문) 해시마다 딱 한 번만 AI 로 만들고 KV 에 보관합니다. 같은 글을 다른 사용자가 열면
 * 저장된 요약을 돌려줄 뿐이라 AI 사용량이 늘지 않습니다. 키가 본문 해시라서 누가 엉뚱한 본문을 보내도
 * 진짜 글의 요약을 덮어쓸 수 없습니다.
 */

export interface Env {
  AI: Ai;
  SUMMARIES: KVNamespace;
}

const MODEL = "@cf/google/gemma-4-26b-a4b-it";
const MAX_TITLE = 300;
const MAX_TEXT = 6000;
/** 짧은 글도 요약합니다(목록의 한 줄 요약 길이·말투를 맞추려고). 본문도 이미지도 없으면 요약할 게 없습니다. */
const MIN_TEXT = 1;
/** 이미지 읽기 — 포털 업로드 파일만, 글당 몇 장까지만(무료 사용량 보호). */
const IMAGE_URL = /^https:\/\/hh\.hana\.hs\.kr\/upfilePath\/[^?#]+\.(png|jpe?g|gif|webp|bmp)$/i;
const MAX_IMAGES = 4;
const MAX_IMAGE_BYTES = 4_000_000;
const OCR_PROMPT = `이 이미지는 한국 고등학교 공지문(가정통신문 등)의 일부다. 이미지 속 글자를 빠짐없이 한국어 원문 그대로 옮겨 적어라.
표는 한 행을 한 줄로, 칸은 ' | '로 구분한다. 학교 로고·워터마크는 무시한다. 설명이나 요약은 붙이지 말고 옮겨 적은 글만 출력한다.
글자가 없는 사진이면 '(글자 없음)'이라고만 쓴다.`;
/** 요약 보관 기간 — 지난 공지는 다시 열 일이 드뭅니다. */
const KEEP_SECONDS = 60 * 60 * 24 * 180;
/**
 * 무료 사용량을 지키는 안전장치 — 새 요약 생성 횟수 제한(전체 하루, IP 당 시간당). 이미 만든 요약을 돌려주는 건 세지 않습니다.
 * 학교 와이파이는 학생들이 한 IP 를 같이 쓰므로 IP 제한은 넉넉히 두고, 실제 한도는 하루 전체 횟수로 겁니다.
 */
const PER_IP_PER_HOUR = 300;
const PER_DAY = 500;

const SYSTEM = `너는 한국 고등학교 포털 공지를 학생에게 요약해 주는 도우미다.
반드시 아래 형식 그대로, 한국어로만 답한다.

한줄: <학생이 이 글에서 알아야 할 핵심 하나, 공백 포함 30자 이내>
- <자세한 요약 1>
- <자세한 요약 2>
- <필요하면 최대 5개까지>

규칙:
- 학생은 제목을 이미 보고 있다. 한줄과 글머리표 모두 제목을 되풀이하거나 바꿔 말하지 말고, 제목만으로는 알 수 없는
  새 정보(날짜·마감·대상·장소·방법·금액·준비물·연락처)를 담는다. 제목에 있는 말을 다시 쓸 필요가 있으면 최소한으로만 쓴다.
- 한줄은 게시판 목록의 작은 칸 두 줄에 들어가야 하므로 반드시 30자를 넘기지 않는다. 가장 중요한 행동이나 날짜 하나만 담고,
  '2026학년도', '안내' 같은 군더더기는 뺀다.
  예) 제목 '2학기 교내 수학경시대회 참가 신청 안내' → 한줄 '10/7(수)까지 포털 신청, 10/14 시청각실'
- 본문이 '첨부파일을 확인하라'는 말뿐이라 새 정보가 없으면, 한줄에 '자세한 내용은 첨부파일 참고'처럼 사실대로 쓰고 글머리표는 비운다.
- 대상, 날짜·시간, 장소, 해야 할 일, 마감, 준비물을 우선한다.
- 인사말, 감사 표현, 제도 배경 설명은 뺀다.
- 글에 없는 내용은 지어내지 않는다. 날짜와 숫자는 본문 그대로 옮긴다.
- 각 글머리표는 짧은 한 문장으로, 존댓말 없이 '~함', '~해야 함'처럼 끝낸다.`;

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    if (url.pathname !== "/summarize") return json({ error: "not_found" }, 404);
    if (req.method !== "POST") return json({ error: "method" }, 405);
    if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);

    let body: { title?: unknown; text?: unknown; images?: unknown };
    try {
      body = await req.json();
    } catch {
      return json({ error: "bad_json" }, 400);
    }
    const title = String(body.title ?? "").trim().slice(0, MAX_TITLE);
    const text = String(body.text ?? "").trim().slice(0, MAX_TEXT);
    const images = (Array.isArray(body.images) ? body.images : [])
      .map((u) => String(u))
      .filter((u) => IMAGE_URL.test(u))
      .slice(0, MAX_IMAGES);
    if (text.length < MIN_TEXT && images.length === 0) return json({ error: "too_short" }, 400);

    // 이미지가 없는 글은 예전과 같은 키(제목+본문)를 써서, 이미 만든 요약을 그대로 돌려줍니다.
    const source = images.length > 0 ? `${title}\n${text}\n${images.join("\n")}` : `${title}\n${text}`;
    const key = "v3:" + (await sha256(source));
    const hit = await env.SUMMARIES.get<Summary>(key, "json");
    if (hit) return json({ ...hit, cached: true });

    const ip = req.headers.get("cf-connecting-ip") ?? "unknown";
    const hour = Math.floor(Date.now() / 3_600_000);
    const day = new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);
    const ipKey = `rl:ip:${ip}:${hour}`;
    const dayKey = `rl:day:${day}`;
    const [ipCount, dayCount] = await Promise.all([count(env, ipKey), count(env, dayKey)]);
    if (ipCount >= PER_IP_PER_HOUR || dayCount >= PER_DAY) return json({ error: "rate_limited" }, 429);
    ctx.waitUntil(
      Promise.all([
        env.SUMMARIES.put(ipKey, String(ipCount + 1), { expirationTtl: 3_700 }),
        env.SUMMARIES.put(dayKey, String(dayCount + 1), { expirationTtl: 60 * 60 * 26 }),
      ]),
    );

    let content = text;
    if (images.length > 0) {
      const read = await readImages(env, images);
      if (read) content = `${text}\n\n[본문 이미지 속 내용]\n${read}`.trim();
    }
    if (!content) return json({ error: "no_content" }, 422);

    let raw: string;
    try {
      const out = (await env.AI.run(MODEL as keyof AiModels, {
        messages: [
          { role: "system", content: SYSTEM },
          { role: "user", content: `제목: ${title}\n본문:\n${content.slice(0, MAX_TEXT * 2)}` },
        ],
        max_tokens: 1200,
        temperature: 0.2,
        // 요약에는 추론 과정이 필요 없어 끕니다 — 켜 두면 추론에 토큰을 다 써 본문이 비었습니다.
        chat_template_kwargs: { enable_thinking: false },
        reasoning_effort: "low",
      } as any)) as { response?: string; choices?: { message?: { content?: string } }[] };
      raw = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
    } catch (e) {
      console.error("ai failed", e);
      return json({ error: "ai_failed" }, 502);
    }

    const summary = parse(raw);
    if (!summary) {
      console.error("unparsable", raw.slice(0, 500));
      return json({ error: "empty" }, 502);
    }
    await env.SUMMARIES.put(key, JSON.stringify(summary), { expirationTtl: KEEP_SECONDS });
    return json({ ...summary, cached: false });
  },
} satisfies ExportedHandler<Env>;

/** 본문 이미지들의 글자를 차례로 읽어 이어 붙입니다. 한 장이 실패해도 나머지는 계속합니다. */
async function readImages(env: Env, urls: string[]): Promise<string> {
  const parts: string[] = [];
  for (const url of urls) {
    try {
      const res = await fetch(url, { cf: { cacheTtl: 86_400 } });
      const type = res.headers.get("content-type") ?? "";
      if (!res.ok || !type.startsWith("image/")) continue;
      const buf = await res.arrayBuffer();
      if (buf.byteLength > MAX_IMAGE_BYTES) continue;
      const out = (await env.AI.run(MODEL as keyof AiModels, {
        messages: [
          {
            role: "user",
            content: [
              { type: "text", text: OCR_PROMPT },
              { type: "image_url", image_url: { url: `data:${type};base64,${base64(buf)}` } },
            ],
          },
        ],
        max_tokens: 2000,
        temperature: 0,
        chat_template_kwargs: { enable_thinking: false },
        reasoning_effort: "low",
      } as any)) as { response?: string; choices?: { message?: { content?: string } }[] };
      const read = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
      if (read && !read.includes("(글자 없음)")) parts.push(read);
    } catch (e) {
      console.error("image read failed", url, e);
    }
  }
  return parts.join("\n\n");
}

function base64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

interface Summary {
  line: string;
  points: string[];
}

/** "한줄: …" 과 "- …" 줄을 뽑습니다. 모델이 형식을 조금 어겨도(굵게 표시, 번호 등) 최대한 살립니다. */
function parse(raw: string): Summary | null {
  let line = "";
  const points: string[] = [];
  for (const rawLine of raw.split("\n")) {
    const l = rawLine.replace(/\*\*/g, "").trim();
    if (!l) continue;
    const head = l.match(/^한\s?줄\s*(요약)?\s*[:：]\s*(.+)$/);
    if (head) {
      line = head[2].trim();
      continue;
    }
    const bullet = l.match(/^(?:[-*•·]|\d+[.)])\s*(.+)$/);
    if (bullet) points.push(bullet[1].trim());
  }
  if (!line && points.length > 0) line = points[0];
  if (!line) return null;
  return { line, points: points.slice(0, 5) };
}

async function count(env: Env, key: string): Promise<number> {
  return parseInt((await env.SUMMARIES.get(key)) ?? "0", 10) || 0;
}

async function sha256(s: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
