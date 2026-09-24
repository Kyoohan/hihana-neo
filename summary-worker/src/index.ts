/**
 * 하이하나 네오 게시글 요약 서버.
 *
 * POST /summarize { title, text } → { line, points, cached }
 *   line   : 게시판 목록에 붙는 한 줄 요약
 *   points : 글 화면 위에 보이는 자세한 요약(글머리표)
 *
 * 키 앞의 v2 는 프롬프트 버전 — 프롬프트를 바꾸면 올려서 예전 요약 대신 새로 만들게 합니다.
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
const MIN_TEXT = 100;
/** 요약 보관 기간 — 지난 공지는 다시 열 일이 드뭅니다. */
const KEEP_SECONDS = 60 * 60 * 24 * 180;
/** 무료 사용량을 지키는 안전장치 — 새 요약 생성 횟수 제한(IP 당 시간당, 전체 하루). */
const PER_IP_PER_HOUR = 40;
const PER_DAY = 300;

const SYSTEM = `너는 한국 고등학교 포털 공지를 학생에게 요약해 주는 도우미다.
반드시 아래 형식 그대로, 한국어로만 답한다.

한줄: <학생이 이 글에서 알아야 할 핵심 하나, 공백 포함 30자 이내>
- <자세한 요약 1>
- <자세한 요약 2>
- <필요하면 최대 5개까지>

규칙:
- 한줄은 게시판 목록의 작은 칸 두 줄에 들어가야 하므로 반드시 30자를 넘기지 않는다. 가장 중요한 행동이나 날짜 하나만 담고,
  제목을 되풀이하지 않으며, '2026학년도', '안내' 같은 군더더기는 뺀다. 예: '예방지도 희망 여부 9/28까지 제출'
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

    let body: { title?: unknown; text?: unknown };
    try {
      body = await req.json();
    } catch {
      return json({ error: "bad_json" }, 400);
    }
    const title = String(body.title ?? "").trim().slice(0, MAX_TITLE);
    const text = String(body.text ?? "").trim().slice(0, MAX_TEXT);
    if (text.length < MIN_TEXT) return json({ error: "too_short" }, 400);

    const key = "v2:" + (await sha256(`${title}\n${text}`));
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

    let raw: string;
    try {
      const out = (await env.AI.run(MODEL as keyof AiModels, {
        messages: [
          { role: "system", content: SYSTEM },
          { role: "user", content: `제목: ${title}\n본문:\n${text}` },
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
