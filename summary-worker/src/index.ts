/**
 * 하이하나 네오 게시글 요약 서버.
 *
 * POST /summarize { title, text, images?, posted? } → { line, points, events, cached }
 *   images : 본문 이미지 주소(포털 업로드 파일만). 가정통신문처럼 본문이 이미지뿐인 글은 이미지 속 글자를
 *            먼저 읽어 낸 뒤 본문과 합쳐 요약합니다.
 *   posted : 글 작성일(YYYY-MM-DD) — 연도 없는 날짜("10/24")를 어느 해로 읽을지 정하는 기준. 키에는 넣지 않습니다.
 *   line   : 게시판 목록에 붙는 한 줄 요약
 *   points : 글 화면 위에 보이는 자세한 요약(글머리표)
 *   events : 글에 적힌 날짜 있는 일정(마감·행사) — 앱이 홈·일정 탭의 통합 일정에 올립니다. grades 는 대상 학년(1~3),
 *            없으면 전 학년 — 앱이 설정한 학년과 맞는 것만 보여 줍니다.
 *
 * GET /weather → 서울특별시 날씨(현재·시간별 3일·일별 7일). 귀가 기간 홈·위젯 안내용. 30분 KV 에 보관해 모든 사용자에게
 *   나눠 줍니다. 기상청 단기예보 조회서비스(공공데이터포털, 서울 중구 소공동=서울시청 격자 60,127)만 씁니다 — 현재는
 *   초단기실황, 6시간은 초단기예보, 그 뒤는 단기예보(+3일까지 1시간, +4·5일은 3시간 간격). Open-Meteo 는 기상청 호출이
 *   실패하거나 키(KMA_SERVICE_KEY, 공공데이터포털 일반 인증키)가 없을 때만 통째로 대신 씁니다. 날씨는 WMO 코드로
 *   바꿔 보내 앱은 제공처를 몰라도 됩니다.
 *
 * POST /brief { date, classes, items } → { headline, body, highlights }
 *   아침 브리핑 — 오늘 수업과 앞으로 7일 일정 중 중요한 것(수행평가·시험·마감)을 AI 가 두세 문장으로 풀어 줍니다.
 *   사람마다 내용이 달라 KV 에 보관하지 않습니다(받은 일정은 답을 만든 뒤 버리고, 횟수만 셉니다).
 *
 * 키 앞의 v7 는 프롬프트 버전 — 프롬프트를 바꾸면 올려서 예전 요약 대신 새로 만들게 합니다.
 * 요약은 (제목 + 본문) 해시마다 딱 한 번만 AI 로 만들고 KV 에 보관합니다. 같은 글을 다른 사용자가 열면
 * 저장된 요약을 돌려줄 뿐이라 AI 사용량이 늘지 않습니다. 키가 본문 해시라서 누가 엉뚱한 본문을 보내도
 * 진짜 글의 요약을 덮어쓸 수 없습니다.
 */

import { bridge } from "./bridge";

export interface Env {
  AI: Ai;
  SUMMARIES: KVNamespace;
  /** 공공데이터포털 '기상청_단기예보 조회서비스' 일반 인증키 — `wrangler secret put KMA_SERVICE_KEY`. 없으면 Open-Meteo 만. */
  KMA_SERVICE_KEY?: string;
  /** 클래스룸 중계 — 기기별 암호문 한 줄씩 (bridge.ts). */
  BRIDGE: D1Database;
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
- 각 글머리표는 짧은 한 문장으로, 존댓말 없이 '~함', '~해야 함'처럼 끝낸다.

글머리표 뒤에, 글에 날짜가 분명히 적힌 일정을 한 줄에 하나씩 아래 형식으로 덧붙인다(없으면 아무 줄도 쓰지 않는다):
일정: <시작 YYYY-MM-DD> | <끝 YYYY-MM-DD, 하루면 비움> | <HH:MM, 시각이 없으면 비움> | <마감 또는 행사> | <대상 학년> | <일정 이름>

일정 규칙:
- 신청·제출·납부·접수 기한은 '마감', 시험·행사·설명회·대회·활동 날짜는 '행사'.
- 대상 학년: 그 일정이 특정 학년만 해당하면 학년 숫자(여러 학년이면 쉼표로, 예: '1' 또는 '1,2'), 모든 학년이거나 글에서 분명하지 않으면 '전체'.
  제목이나 본문 첫머리에 대상 학년('2학년 대상')이 있으면 그 글의 일정은 그 학년이다.
- 한 글에 학년마다 다른 일정이 섞여 있으면(예: 1학년 중국어 수행평가, 2학년 미적분 수행평가) 같은 날이어도 학년마다 따로 한 줄씩 쓴다.
  학년은 대상 학년 칸에만 쓰고 일정 이름에는 다시 쓰지 않는다.
- 대상이 'N기'(예: 16기, 17기)로 적혀 있으면 사용자 글 위의 '학교 정보'에 있는 기수-학년 표로 학년 숫자로 바꿔 대상 학년 칸에 쓴다.
  일정 이름에는 기수도 학년도 쓰지 않는다.
- 시각 대신 '면학 1타임'처럼 면학 타임이 적혀 있으면 '학교 정보'의 그 타임 시작 시각을 HH:MM 칸에 쓴다. 평일(월~금)과
  주말(토·일) 타임 시각이 다르니 그 날짜의 요일로 고른다. 본문에 시각이 따로 적혀 있으면 본문 시각을 쓴다.
- 일정 이름은 20자 이내로, 무엇을 하는 날인지 바로 알게 쓴다. 예) '수학경시대회 신청 마감', '2학기 중간고사', '진로 설명회'.
- 연도가 없는 날짜는 글 작성일과 같은 해, 작성일보다 앞이면 다음 해로 읽는다. '이번 주 금요일'처럼 글 작성일이 있어야 셀 수 있는 날짜는 작성일 기준으로 센다.
- 급식·식단 운영 안내(주간 급식, 식단표)는 일정으로 쓰지 않는다.
- 글(제목·본문)에 날짜가 직접 적혀 있지 않으면 일정을 쓰지 않는다. 글 작성일은 '이번 주 금요일' 같은 날짜를 셀 때만 쓰고,
  작성일 자체를 일정 날짜로 쓰지 않는다.
- 날짜를 정확히 알 수 없는 일정('추후 공지', '10월 중')은 쓰지 않는다. 지난 일정도 날짜가 분명하면 쓴다.
- 기간(10/20~10/24)은 시작과 끝에 나눠 적고, 요일마다 다른 시간표처럼 많은 날짜가 나열되면 전체 기간 하나로 줄인다. 최대 10개.`;

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    if (url.pathname === "/weather") return weather(req, env, url);
    if (url.pathname.startsWith("/bridge/")) return bridge(req, env, url);
    if (url.pathname === "/brief") return brief(req, env, ctx);
    if (url.pathname === "/menu") return menu(req, env, ctx);
    if (url.pathname !== "/summarize") return json({ error: "not_found" }, 404);
    if (req.method !== "POST") return json({ error: "method" }, 405);
    if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);

    let body: { title?: unknown; text?: unknown; images?: unknown; posted?: unknown };
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
    const posted = String(body.posted ?? "").match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? "";
    if (text.length < MIN_TEXT && images.length === 0) return json({ error: "too_short" }, 400);

    // 이미지가 없는 글은 예전과 같은 키(제목+본문)를 써서, 이미 만든 요약을 그대로 돌려줍니다.
    const source = images.length > 0 ? `${title}\n${text}\n${images.join("\n")}` : `${title}\n${text}`;
    const key = "v7:" + (await sha256(source));
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
          { role: "user", content: `${schoolInfo(posted)}\n${posted ? `글 작성일: ${posted}\n` : ""}제목: ${title}\n본문:\n${content.slice(0, MAX_TEXT * 2)}` },
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
    // 글에 날짜 표현이 하나도 없으면 일정은 버립니다 — 날짜 없는 클래스룸 공지('조 편성', '주제를 정해 두세요')에도 모델이
    // 글 작성일을 일정 날짜로 지어내 '미적분2 조별 학습' 같은 오늘 일정이 생겼습니다.
    if (!hasDateHint(`${title}\n${content}`)) summary.events = [];
    await env.SUMMARIES.put(key, JSON.stringify(summary), { expirationTtl: KEEP_SECONDS });
    return json({ ...summary, cached: false });
  },
} satisfies ExportedHandler<Env>;

/**
 * 일정 뽑기에 필요한 학교 정보 — 기수와 학년(학년도는 3월에 시작: 2026학년도엔 17기가 1학년), 평일·주말 면학 타임 시각
 * (앱 Timetable.Sessions 와 같은 값). 글 작성일(없으면 오늘) 기준.
 */
function schoolInfo(posted: string): string {
  const d = posted || new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);
  const [y, m] = d.split("-").map(Number);
  const schoolYear = m < 3 ? y - 1 : y;
  const first = schoolYear - 2009; // 1학년 기수
  return `학교 정보 (${schoolYear}학년도):
- 기수와 학년: ${first - 2}기 = 3학년, ${first - 1}기 = 2학년, ${first}기 = 1학년
- 평일 면학: 0타임 16:20~17:50, 1타임 19:00~21:00, 2타임 21:30~23:10
- 주말 면학: 1타임 13:30~15:30, 2타임 16:00~17:50, 3타임 19:00~21:00, 4타임 21:30~23:10
`;
}

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
  events: SummaryEvent[];
}

/** 글에서 뽑은 일정 한 건 — 날짜는 YYYY-MM-DD, 시각은 HH:MM(없으면 생략). */
interface SummaryEvent {
  title: string;
  date: string;
  end?: string;
  time?: string;
  /** 신청·제출 기한이면 true, 행사·시험이면 false. */
  deadline: boolean;
  /** 대상 학년(1~3) — 전 학년이거나 분명하지 않으면 생략. */
  grades?: number[];
}

const MAX_EVENTS = 10;

/** "한줄: …", "- …", "일정: …" 줄을 뽑습니다. 모델이 형식을 조금 어겨도(굵게 표시, 번호 등) 최대한 살립니다. */
function parse(raw: string): Summary | null {
  let line = "";
  const points: string[] = [];
  const events: SummaryEvent[] = [];
  for (const rawLine of raw.split("\n")) {
    const l = rawLine.replace(/\*\*/g, "").trim();
    if (!l) continue;
    const head = l.match(/^한\s?줄\s*(요약)?\s*[:：]\s*(.+)$/);
    if (head) {
      line = head[2].trim();
      continue;
    }
    const ev = l.match(/^(?:[-*•·]\s*)?일정\s*[:：]\s*(.+)$/);
    if (ev) {
      const parsed = parseEvent(ev[1]);
      const same = (e: SummaryEvent) => e.date === parsed!.date && e.title === parsed!.title && String(e.grades ?? "") === String(parsed!.grades ?? "");
      if (parsed && !events.some(same)) events.push(parsed);
      continue;
    }
    const bullet = l.match(/^(?:[-*•·]|\d+[.)])\s*(.+)$/);
    if (bullet) points.push(bullet[1].trim());
  }
  if (!line && points.length > 0) line = points[0];
  if (!line) return null;
  return { line, points: points.slice(0, 5), events: events.slice(0, MAX_EVENTS) };
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 날짜를 가리키는 표현 — '10/27', '10.27', '10월 27일', '27일', '2026-10-27', 요일, '오늘·내일·모레', '이번·다음 주', '주말'. */
const DATE_HINT = /\d{1,2}\s*[/.]\s*\d{1,2}|\d{1,2}\s*월\s*\d{1,2}|\d{1,2}\s*일|\d{4}-\d{2}-\d{2}|[월화수목금토일]요일|\(\s*[월화수목금토일]\s*\)|오늘|내일|모레|글피|이번\s*주|다음\s*주|주말/;

function hasDateHint(text: string): boolean {
  return DATE_HINT.test(text);
}

/** 대상 학년 칸 — '1', '1,2', '2학년', '1·3학년' 이면 학년 숫자, '전체'·'공통'·빈칸이면 전 학년. 학년 칸이 아니면 null. */
const GRADE_CELL = /^(?:전체|전\s*학년|공통|모두|전원|-|[1-3]\s*(?:학년)?(?:\s*[,·/및와]\s*[1-3]\s*(?:학년)?)*)$/;

/**
 * "2026-10-24 | 2026-10-25 | 13:00 | 마감 | 2 | 수학경시대회 신청" → 일정. 날짜가 틀리면 버립니다. 대상 학년 칸이 없는 예전
 * 형식("… | 마감 | 이름")도 읽습니다(전 학년).
 */
function parseEvent(s: string): SummaryEvent | null {
  const cells = s.split("|").map((c) => c.trim());
  if (cells.length < 5) return null;
  const [date, endRaw, timeRaw, kind, ...rest] = cells;
  let grades: number[] | undefined;
  if (rest.length >= 2 && GRADE_CELL.test(rest[0])) {
    const found = [...new Set((rest.shift() as string).match(/[1-3]/g)?.map(Number) ?? [])].sort();
    if (found.length > 0 && found.length < 3) grades = found;
  }
  const title = rest.join(" | ").replace(/^['"]|['"]$/g, "").trim().slice(0, 40);
  if (!DATE.test(date) || !title || Number.isNaN(Date.parse(date))) return null;
  const end = DATE.test(endRaw) && endRaw > date ? endRaw : undefined;
  const time = timeRaw.match(/^(\d{1,2}):(\d{2})$/);
  const hhmm = time && Number(time[1]) < 24 && Number(time[2]) < 60 ? `${time[1].padStart(2, "0")}:${time[2]}` : undefined;
  return { title, date, end, time: hhmm, deadline: kind.includes("마감"), ...(grades ? { grades } : {}) };
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

// MARK: - 날씨

/**
 * 날씨를 받는 곳 — 위경도(Open-Meteo)와 기상청 격자. 사용자 위치는 받지 않고, 앱이 둘 중 하나를 고릅니다(?place=).
 * 서울: 서울특별시청(60,127) — 귀가 기간. 하나고: 하나고등학교(은평구 진관동, 59,128) — 평소. place 가 없으면 예전 앱처럼 서울.
 */
interface Place { name: string; lat: number; lon: number; nx: number; ny: number }
const PLACES: Record<string, Place> = {
  seoul: { name: "서울", lat: 37.5665, lon: 126.978, nx: 60, ny: 127 },
  hana: { name: "하나고", lat: 37.6413, lon: 126.9371, nx: 59, ny: 128 },
};
const WEATHER_KEY = (place: string) => `weather:${place}:v3`;
const WEATHER_SECONDS = 30 * 60;

interface Hour { t: string; temp: number | null; code: number | null; pop: number | null }
interface Day { date: string; code: number | null; max: number | null; min: number | null; pop: number | null }
interface Forecast { source: string; current: { time: string; temp: number | null; code: number | null }; hourly: Hour[]; daily: Day[] }

async function weather(req: Request, env: Env, url: URL): Promise<Response> {
  if (req.method !== "GET") return json({ error: "method" }, 405);
  if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);
  const placeKey = PLACES[url.searchParams.get("place") ?? ""] ? (url.searchParams.get("place") as string) : "seoul";
  const place = PLACES[placeKey];
  const cached = await env.SUMMARIES.get(WEATHER_KEY(placeKey));
  if (cached) return new Response(cached, { headers: { "content-type": "application/json; charset=utf-8" } });

  // 기상청만 씁니다. 기상청이 실패(또는 키 없음)할 때만 Open-Meteo 로 통째로 대신합니다 — 섞어 채우지 않습니다.
  let forecast: Forecast | null = null;
  if (env.KMA_SERVICE_KEY) {
    forecast = await kmaForecast(env.KMA_SERVICE_KEY, place).catch((e) => { console.log("kma failed", String(e)); return null; });
  }
  if (!forecast) forecast = await openMeteo(place).catch((e) => { console.log("open-meteo failed", String(e)); return null; });
  if (!forecast) return json({ error: "upstream" }, 502);

  // 대신 쓴 응답은 짧게(5분) 보관해 기상청이 돌아오면 곧 바뀌게 합니다.
  const body = JSON.stringify({ place: place.name, ...forecast, updated: new Date().toISOString() });
  await env.SUMMARIES.put(WEATHER_KEY(placeKey), body, { expirationTtl: forecast.source === "kma" ? WEATHER_SECONDS : 5 * 60 });
  return new Response(body, { headers: { "content-type": "application/json; charset=utf-8" } });
}

// MARK: Open-Meteo (기상청이 안 될 때만)

interface OpenMeteo {
  current?: { time: string; temperature_2m: number | null; weather_code: number | null };
  hourly?: { time: string[]; temperature_2m: (number | null)[]; weather_code: (number | null)[]; precipitation_probability: (number | null)[] };
  daily?: {
    time: string[];
    weather_code: (number | null)[];
    temperature_2m_max: (number | null)[];
    temperature_2m_min: (number | null)[];
    precipitation_probability_max: (number | null)[];
  };
}

async function openMeteo(place: Place): Promise<Forecast> {
  const api = new URL("https://api.open-meteo.com/v1/forecast");
  api.search = new URLSearchParams({
    latitude: String(place.lat),
    longitude: String(place.lon),
    current: "temperature_2m,weather_code",
    hourly: "temperature_2m,weather_code,precipitation_probability",
    daily: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
    timezone: "Asia/Seoul",
    forecast_days: "7",
  }).toString();
  const res = await fetch(api.toString(), { headers: { "user-agent": "hihana-neo-weather" } });
  if (!res.ok) throw new Error(`open-meteo ${res.status}`);
  const data = (await res.json()) as OpenMeteo;
  if (!data.current || !data.hourly || !data.daily) throw new Error("open-meteo shape");
  const now = data.current.time.slice(0, 13);
  return {
    source: "open-meteo",
    current: { time: data.current.time, temp: data.current.temperature_2m, code: data.current.weather_code },
    hourly: data.hourly.time
      .map((t, i) => ({ t, temp: data.hourly!.temperature_2m[i], code: data.hourly!.weather_code[i], pop: data.hourly!.precipitation_probability[i] }))
      .filter((h) => h.t.slice(0, 13) >= now)
      .slice(0, 72),
    daily: data.daily.time.map((date, i) => ({
      date,
      code: data.daily!.weather_code[i],
      max: data.daily!.temperature_2m_max[i],
      min: data.daily!.temperature_2m_min[i],
      pop: data.daily!.precipitation_probability_max[i],
    })),
  };
}

// MARK: 기상청 단기예보 (공공데이터포털 VilageFcstInfoService_2.0)

const KMA_BASE = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";

interface KmaItem { category: string; fcstDate?: string; fcstTime?: string; fcstValue?: string; obsrValue?: string }

/** 한국 시각의 날짜·시각 조각. */
function kst(date: Date) {
  const k = new Date(date.getTime() + 9 * 3600_000);
  const ymd = k.toISOString().slice(0, 10).replaceAll("-", "");
  return { ymd, hour: k.getUTCHours(), minute: k.getUTCMinutes(), date: k };
}

async function kmaCall(key: string, op: string, baseDate: string, baseTime: string, place: Place): Promise<KmaItem[]> {
  const url = new URL(`${KMA_BASE}/${op}`);
  // 포털의 '인코딩' 키를 넣어도 되게 한 번 풀어서 다시 인코딩합니다.
  const serviceKey = key.includes("%") ? decodeURIComponent(key) : key;
  url.search = new URLSearchParams({
    // 단기예보는 +5일까지(요소 12종 × 시간) 라 넉넉히 한 번에 받습니다.
    serviceKey, pageNo: "1", numOfRows: "3000", dataType: "JSON",
    base_date: baseDate, base_time: baseTime, nx: String(place.nx), ny: String(place.ny),
  }).toString();
  const res = await fetch(url.toString());
  const text = await res.text();
  let data: any;
  try { data = JSON.parse(text); } catch { throw new Error(`kma ${op} not json: ${text.slice(0, 120)}`); }
  const code = data?.response?.header?.resultCode;
  if (code !== "00") throw new Error(`kma ${op} ${code} ${data?.response?.header?.resultMsg}`);
  return (data.response.body?.items?.item ?? []) as KmaItem[];
}

/** 하늘(SKY)·강수형태(PTY)·낙뢰 → WMO 코드. 앱은 이 코드로 이름·아이콘을 고릅니다. */
function kmaCode(sky: number | null, pty: number | null, lightning = false): number | null {
  if (lightning) return 95;
  switch (pty) {
    case 1: return 61; // 비
    case 2: return 66; // 비/눈 → 진눈깨비
    case 3: return 71; // 눈
    case 4: return 80; // 소나기
    case 5: return 51; // 빗방울
    case 6: return 56; // 빗방울눈날림
    case 7: return 77; // 눈날림
  }
  switch (sky) {
    case 1: return 0; // 맑음
    case 3: return 2; // 구름많음
    case 4: return 3; // 흐림
  }
  return null;
}

/** 강수(가장 심한 것) 우선, 없으면 낮 동안 가장 흔한 하늘. */
function representative(codes: number[]): number | null {
  if (!codes.length) return null;
  const severity = (c: number) => (c >= 95 ? 6 : c >= 71 && c <= 77 ? 5 : c === 66 || c === 56 ? 4 : c >= 51 && c <= 82 ? 3 : 0);
  const wet = codes.filter((c) => severity(c) > 0);
  if (wet.length) return wet.reduce((a, b) => (severity(b) > severity(a) ? b : a));
  const counts = new Map<number, number>();
  for (const c of codes) counts.set(c, (counts.get(c) ?? 0) + 1);
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || b[0] - a[0])[0][0];
}

async function kmaForecast(key: string, place: Place): Promise<Forecast> {
  const now = new Date();
  const k = kst(now);
  // 가이드 기준 — 초단기실황: 매시 정시 자료를 10분 이후. 초단기예보: 매시 30분 자료를 45분 이후.
  const ncstAt = kst(new Date(now.getTime() - 10 * 60_000));
  const fcstAt = kst(new Date(now.getTime() - 45 * 60_000));
  const pad = (n: number) => String(n).padStart(2, "0");
  // 단기예보: 02·05·08·11·14·17·20·23시 발표, 10분쯤 올라옵니다.
  const vilAt = kst(new Date(now.getTime() - 10 * 60_000));
  let vilHour = [23, 20, 17, 14, 11, 8, 5, 2].find((h) => h <= vilAt.hour);
  let vilDate = vilAt.ymd;
  if (vilHour === undefined) {
    vilHour = 23;
    vilDate = kst(new Date(now.getTime() - 24 * 3600_000)).ymd;
  }

  // 단기예보가 바탕이라 그것만 꼭 필요하고, 실황·초단기예보가 잠깐 안 되면 단기예보만으로 채웁니다.
  const optional = (op: string, d: string, t: string) =>
    kmaCall(key, op, d, t, place).catch((e) => { console.log(`kma ${op} skipped`, String(e)); return [] as KmaItem[]; });
  const [ncst, ultra, vil, morning] = await Promise.all([
    optional("getUltraSrtNcst", ncstAt.ymd, `${pad(ncstAt.hour)}00`),
    optional("getUltraSrtFcst", fcstAt.ymd, `${pad(fcstAt.hour)}30`),
    kmaCall(key, "getVilageFcst", vilDate, `${pad(vilHour)}00`, place),
    // 오늘 최저·최고(TMN 06시, TMX 15시)는 그 시각이 지나면 최신 발표에서 빠지므로 오늘 02시 발표에서 가져옵니다.
    k.hour >= 3 ? kmaCall(key, "getVilageFcst", k.ymd, "0200", place).catch(() => [] as KmaItem[]) : Promise.resolve([] as KmaItem[]),
  ]);

  const iso = (d: string, t: string) => `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}T${t.slice(0, 2)}:00`;
  const num = (v: string | undefined) => {
    const n = Number(v);
    return v == null || Number.isNaN(n) || n <= -900 || n >= 900 ? null : n;
  };

  // 시간별 — 단기예보를 깔고, 6시간은 더 정확한 초단기예보로 덮습니다(강수확률은 단기예보에만 있음).
  type Slot = { temp: number | null; sky: number | null; pty: number | null; pop: number | null; lgt: boolean };
  const slots = new Map<string, Slot>();
  const slot = (t: string) => slots.get(t) ?? (slots.set(t, { temp: null, sky: null, pty: null, pop: null, lgt: false }), slots.get(t)!);
  for (const it of vil) {
    if (!it.fcstDate || !it.fcstTime) continue;
    const s = slot(iso(it.fcstDate, it.fcstTime));
    if (it.category === "TMP") s.temp = num(it.fcstValue);
    else if (it.category === "SKY") s.sky = num(it.fcstValue);
    else if (it.category === "PTY") s.pty = num(it.fcstValue);
    else if (it.category === "POP") s.pop = num(it.fcstValue);
  }
  for (const it of ultra) {
    if (!it.fcstDate || !it.fcstTime) continue;
    const s = slot(iso(it.fcstDate, it.fcstTime));
    if (it.category === "T1H") s.temp = num(it.fcstValue);
    else if (it.category === "SKY") s.sky = num(it.fcstValue);
    else if (it.category === "PTY") s.pty = num(it.fcstValue);
    else if (it.category === "LGT") s.lgt = (num(it.fcstValue) ?? 0) > 0;
    else if (it.category === "POP") s.pop = num(it.fcstValue) ?? s.pop; // 2026-06-23 부터 초단기예보에도 강수확률
  }

  // 지금 — 초단기실황의 기온·강수형태, 하늘은 가장 가까운 초단기예보 시간에서.
  const obs = (cat: string) => num(ncst.find((i) => i.category === cat)?.obsrValue);
  const nowIso = `${k.date.toISOString().slice(0, 13)}:00`;
  const nextSlots = [...slots.entries()].filter(([t]) => t >= nowIso).sort(([a], [b]) => a.localeCompare(b));
  const nearest = nextSlots[0]?.[1];
  const curPty = obs("PTY");
  // 실황이 없으면(잠깐 실패) 가장 가까운 예보 시간으로.
  const current = {
    time: nowIso,
    temp: obs("T1H") ?? nearest?.temp ?? null,
    code: kmaCode(nearest?.sky ?? null, curPty ?? nearest?.pty ?? null, nearest?.lgt ?? false),
  };

  const hourly: Hour[] = [];
  // 지금 시간 칸 — 예보엔 다음 시간부터라 실황으로 채웁니다(귀가일 23시대처럼 남은 칸이 지금뿐일 때 필요).
  if (!slots.has(nowIso)) hourly.push({ t: nowIso, temp: current.temp, code: current.code, pop: nearest?.pop ?? null });
  for (const [t, s] of nextSlots) hourly.push({ t, temp: s.temp, code: kmaCode(s.sky, s.pty, s.lgt), pop: s.pop });

  // 일별 — 최저·최고는 TMN·TMX(없으면 시간별 기온), 강수확률은 그날 최대, 날씨는 06~21시 대표값.
  const days = new Map<string, { codes: number[]; temps: number[]; pops: number[]; max: number | null; min: number | null }>();
  const day = (d: string) => days.get(d) ?? (days.set(d, { codes: [], temps: [], pops: [], max: null, min: null }), days.get(d)!);
  for (const it of [...morning, ...vil]) {
    if (!it.fcstDate) continue;
    if (it.category === "TMX") day(it.fcstDate).max ??= num(it.fcstValue);
    if (it.category === "TMN") day(it.fcstDate).min ??= num(it.fcstValue);
  }
  for (const [t, s] of slots) {
    const d = day(t.slice(0, 10).replaceAll("-", ""));
    if (s.temp != null) d.temps.push(s.temp);
    if (s.pop != null) d.pops.push(s.pop);
    const hour = Number(t.slice(11, 13));
    const c = kmaCode(s.sky, s.pty, s.lgt);
    if (c != null && hour >= 6 && hour <= 21) d.codes.push(c);
  }
  // 단기예보가 그날을 몇 시간만 덮으면(마지막 날의 자정 무렵 등) 하루 값이 틀리므로 그날은 뺍니다 —
  // 최고·최저 예보(TMX·TMN)가 둘 다 있거나 시간별 자료가 하루 대부분(20시간 이상)을 덮는 날만. (+4·5일은 3시간 간격이라 TMX·TMN 으로)
  const daily: Day[] = [...days.entries()]
    .filter(([, v]) => (v.max != null && v.min != null) || v.temps.length >= 20)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([d, v]) => ({
      date: `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`,
      code: representative(v.codes),
      max: v.max ?? (v.temps.length ? Math.max(...v.temps) : null),
      min: v.min ?? (v.temps.length ? Math.min(...v.temps) : null),
      pop: v.pops.length ? Math.max(...v.pops) : null,
    }));

  return { source: "kma", current, hourly: hourly.slice(0, 72), daily };
}

// MARK: - 아침 브리핑

/** 하루 전체·IP 당 시간당 횟수 — 한 사람이 하루 몇 번(아침 알림 + 일정이 바뀔 때) 부르는 정도라 요약보다 작게 둡니다. */
const BRIEF_PER_DAY = 400;
const BRIEF_PER_IP_PER_HOUR = 40;
const BRIEF_MAX_CLASSES = 10;
const BRIEF_MAX_ITEMS = 30;

const BRIEF_SYSTEM = `너는 한국 기숙형 고등학교 학생에게 아침 브리핑을 써 주는 도우미다. 오늘 날짜, 오늘 수업, 오늘부터 7일 동안의 일정 목록을 받는다.
반드시 아래 형식 그대로, 한국어로만 답한다.

제목: <오늘 아침 가장 먼저 알아야 할 것 하나, 공백 포함 24자 이내>
브리핑: <두세 문장. 오늘 하루에 무엇이 있는지와, 이번 주에 미리 챙길 것을 이어서 설명>
중요: <YYYY-MM-DD> | <일정 이름, 20자 이내> | <왜 중요한지 또는 해야 할 일, 28자 이내>

규칙:
- 중요 줄은 최대 4개, 날짜순이 아니라 중요한 순서로 쓴다: ① 종류가 '시험·수행평가'인 것(며칠 뒤여도 맨 앞) ② 과제 제출·신청 마감
  ③ 오늘 행사 ④ 휴업·귀가처럼 일과가 바뀌는 날. 신청 '시작'은 마감이 아니다.
- 방과후 강의 기간처럼 길게 이어지는 운영 기간은 그 기간이 끝나는 날이 아니면 쓰지 않는다.
- 날짜는 목록에 붙은 요일·오늘 기준 말을 그대로 써서 '오늘', '내일', '모레' 또는 '토요일(10/24)'처럼 말한다. 요일을 직접 세지 않는다. 중요 줄의 날짜 칸만 YYYY-MM-DD 로 쓴다.
- 목록에 없는 일정·준비물·시험 범위를 지어내지 않는다. 일정 이름은 목록의 말을 그대로 쓰되 길면 줄인다.
- 오늘 일정에 휴업일·공휴일·방학·귀가가 있으면 수업 목록은 무시하고 수업 이야기를 하지 않는다. 수업 목록이 비어 있어도 그렇다.
- 수업은 과목을 하나하나 늘어놓지 말고, 일정과 이어질 때만(예: 수행평가가 있는 과목) 언급한다.
- 문장은 '~해요', '~예요'처럼 부드러운 존댓말로 쓰고, 인사말·이모지·느낌표는 쓰지 않는다.
- 중요한 일정이 하나도 없으면 브리핑에 '이번 주는 큰 마감이 없어요'처럼 사실대로 쓰고 중요 줄은 쓰지 않는다.`;

interface BriefItem { date: string; end?: string; time?: string; title: string; kind: string; from?: string }
interface BriefHighlight { date: string; title: string; note: string }

async function brief(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);
  let body: { date?: unknown; classes?: unknown; items?: unknown };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }
  const date = String(body.date ?? "");
  if (!DATE.test(date) || Number.isNaN(Date.parse(date))) return json({ error: "bad_date" }, 400);
  const clip = (v: unknown, n: number) => String(v ?? "").replace(/\s+/g, " ").trim().slice(0, n);
  const classes = (Array.isArray(body.classes) ? body.classes : []).map((c) => clip(c, 60)).filter(Boolean).slice(0, BRIEF_MAX_CLASSES);
  const items: BriefItem[] = (Array.isArray(body.items) ? body.items : [])
    .map((raw: any) => ({
      date: clip(raw?.date, 10),
      end: DATE.test(clip(raw?.end, 10)) ? clip(raw?.end, 10) : undefined,
      time: clip(raw?.time, 11) || undefined,
      title: clip(raw?.title, 60),
      kind: clip(raw?.kind, 12),
      from: clip(raw?.from, 60) || undefined,
    }))
    .filter((i) => DATE.test(i.date) && i.title)
    .slice(0, BRIEF_MAX_ITEMS);

  const ip = req.headers.get("cf-connecting-ip") ?? "unknown";
  const hour = Math.floor(Date.now() / 3_600_000);
  const day = new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);
  const ipKey = `rl:brief:ip:${ip}:${hour}`;
  const dayKey = `rl:brief:day:${day}`;
  const [ipCount, dayCount] = await Promise.all([count(env, ipKey), count(env, dayKey)]);
  if (ipCount >= BRIEF_PER_IP_PER_HOUR || dayCount >= BRIEF_PER_DAY) return json({ error: "rate_limited" }, 429);
  ctx.waitUntil(
    Promise.all([
      env.SUMMARIES.put(ipKey, String(ipCount + 1), { expirationTtl: 3_700 }),
      env.SUMMARIES.put(dayKey, String(dayCount + 1), { expirationTtl: 60 * 60 * 26 }),
    ]),
  );

  // 날짜마다 요일과 오늘 기준 말(내일·모레)을 붙여 줍니다 — 모델이 요일을 스스로 세면 하루씩 틀렸습니다.
  const KO_DAYS = ["일", "월", "화", "수", "목", "금", "토"];
  const base = Date.parse(date);
  const label = (d: string) => {
    const diff = Math.round((Date.parse(d) - base) / 86_400_000);
    const rel = diff === 0 ? "오늘" : diff === 1 ? "내일" : diff === 2 ? "모레" : diff < 0 ? `${-diff}일 전` : `${diff}일 뒤`;
    return `${d}(${KO_DAYS[new Date(Date.parse(d)).getUTCDay()]}·${rel})`;
  };
  const weekday = KO_DAYS[new Date(base).getUTCDay()];
  const lines = items.map((i) => {
    const when = i.end && i.end > i.date ? `${label(i.date)}~${label(i.end)}` : label(i.date);
    return `- ${when}${i.time ? ` ${i.time}` : ""} | ${i.kind || "일정"} | ${i.title}${i.from ? ` (${i.from})` : ""}`;
  });
  const prompt = `오늘: ${date} (${weekday})
오늘 수업:
${classes.length ? classes.map((c) => `- ${c}`).join("\n") : "(없음)"}
일정(오늘부터 7일):
${lines.length ? lines.join("\n") : "(없음)"}`;

  let raw: string;
  try {
    const out = (await env.AI.run(MODEL as keyof AiModels, {
      messages: [
        { role: "system", content: BRIEF_SYSTEM },
        { role: "user", content: prompt },
      ],
      max_tokens: 600,
      temperature: 0.3,
      chat_template_kwargs: { enable_thinking: false },
      reasoning_effort: "low",
    } as any)) as { response?: string; choices?: { message?: { content?: string } }[] };
    raw = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
  } catch (e) {
    console.error("brief ai failed", e);
    return json({ error: "ai_failed" }, 502);
  }
  const parsed = parseBrief(raw, date);
  if (!parsed) {
    console.error("brief unparsable", raw.slice(0, 500));
    return json({ error: "empty" }, 502);
  }
  return json(parsed);
}

/** "제목: …", "브리핑: …"(다음 줄까지 이어질 수 있음), "중요: 날짜 | 이름 | 설명" 을 뽑습니다. 7일 밖의 날짜는 버립니다. */
function parseBrief(raw: string, today: string): { headline: string; body: string; highlights: BriefHighlight[] } | null {
  let headline = "";
  const body: string[] = [];
  const highlights: BriefHighlight[] = [];
  let inBody = false;
  const last = new Date(Date.parse(today) + 7 * 86_400_000).toISOString().slice(0, 10);
  for (const rawLine of raw.split("\n")) {
    const l = rawLine.replace(/\*\*/g, "").trim();
    if (!l) continue;
    const head = l.match(/^제목\s*[:：]\s*(.+)$/);
    if (head) {
      headline = head[1].trim();
      inBody = false;
      continue;
    }
    const b = l.match(/^브리핑\s*[:：]\s*(.*)$/);
    if (b) {
      if (b[1].trim()) body.push(b[1].trim());
      inBody = true;
      continue;
    }
    const h = l.match(/^(?:[-*•·]\s*)?중요\s*[:：]\s*(.+)$/);
    if (h) {
      inBody = false;
      const [date, title, ...note] = h[1].split("|").map((c) => c.trim());
      if (DATE.test(date ?? "") && date >= today && date <= last && title && highlights.length < 4) {
        highlights.push({ date, title: title.slice(0, 40), note: note.join(" ").slice(0, 60) });
      }
      continue;
    }
    if (inBody) body.push(l);
  }
  if (!headline || body.length === 0) return null;
  return { headline: headline.slice(0, 60), body: body.join(" ").slice(0, 400), highlights };
}

// MARK: - 급식 특식 고르기

const MENU_PER_IP_PER_HOUR = 60;
const MENU_MAX_DISHES = 20;

const MENU_SYSTEM = `너는 한국 기숙형 고등학교 급식 한 끼의 메뉴 목록을 받아, 학생들이 가장 반가워할 '특식'을 고른다.

특식: 평소 급식에 잘 나오지 않고 기대할 만한 메인 요리나 디저트 — 예: 순살허브치킨구이, 등갈비바베큐, 크림파스타, 수제버거, 연어스테이크, 츄러스.
특식이 아닌 것: 밥(보리밥·잡곡밥), 국·탕·찌개(마라탕 포함), 김치·나물·무침·조림·볶음 같은 밑반찬, 계란찜, 만두(갈비손만두찜 포함),
생선구이처럼 자주 나오는 반찬, 우유·요구르트·음료·과일.

규칙:
- 목록에 있는 이름을 글자 하나 바꾸지 말고 그대로, 한 줄에 하나씩, 가장 특별한 것부터 1~2개만 쓴다.
- 특식이 하나뿐이면 하나만 쓴다. 둘째가 첫째만큼 특별하지 않으면 쓰지 않는다.
- 특식이 없으면 그 끼니의 메인 요리 하나만 쓴다.
- 다른 말·번호·기호는 쓰지 않는다.`;

/** 한 끼 메뉴에서 특식 1~2개 — 같은 메뉴(학교 전체가 같음)는 KV 에 사흘 둬 한 번만 모델을 부릅니다. 저장하는 건 메뉴 이름뿐입니다. */
async function menu(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);
  let body: { dishes?: unknown };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }
  const dishes = (Array.isArray(body.dishes) ? body.dishes : [])
    .map((d) => String(d ?? "").replace(/\s+/g, " ").trim().slice(0, 40))
    .filter(Boolean)
    .slice(0, MENU_MAX_DISHES);
  if (dishes.length === 0) return json({ error: "empty" }, 400);

  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(dishes.join("|")));
  const cacheKey = `menu:${[...new Uint8Array(digest)].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("")}`;
  const cached = await env.SUMMARIES.get(cacheKey);
  if (cached) return json({ highlights: JSON.parse(cached) });

  const ip = req.headers.get("cf-connecting-ip") ?? "unknown";
  const ipKey = `rl:menu:ip:${ip}:${Math.floor(Date.now() / 3_600_000)}`;
  const ipCount = await count(env, ipKey);
  if (ipCount >= MENU_PER_IP_PER_HOUR) return json({ error: "rate_limited" }, 429);
  ctx.waitUntil(env.SUMMARIES.put(ipKey, String(ipCount + 1), { expirationTtl: 3_700 }));

  let raw: string;
  try {
    const out = (await env.AI.run(MODEL as keyof AiModels, {
      messages: [
        { role: "system", content: MENU_SYSTEM },
        { role: "user", content: dishes.map((d) => `- ${d}`).join("\n") },
      ],
      max_tokens: 80,
      temperature: 0.1,
      chat_template_kwargs: { enable_thinking: false },
      reasoning_effort: "low",
    } as any)) as { response?: string; choices?: { message?: { content?: string } }[] };
    raw = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
  } catch (e) {
    console.error("menu ai failed", e);
    return json({ error: "ai_failed" }, 502);
  }
  // 목록에 있는 이름만, 앞뒤 기호를 걷어 내고 둘까지.
  const picked: string[] = [];
  for (const line of raw.split("\n")) {
    const name = line.replace(/^[\s\-*•\d.)]+/, "").trim();
    const hit = dishes.find((d) => d === name) ?? dishes.find((d) => name && (d.includes(name) || name.includes(d)));
    if (hit && !picked.includes(hit)) picked.push(hit);
    if (picked.length === 2) break;
  }
  if (picked.length === 0) {
    console.error("menu unparsable", raw.slice(0, 200));
    return json({ error: "empty" }, 502);
  }
  ctx.waitUntil(env.SUMMARIES.put(cacheKey, JSON.stringify(picked), { expirationTtl: 3 * 86_400 }));
  return json({ highlights: picked });
}
