/**
 * 하이하나 네오 게시글 요약 서버.
 *
 * POST /summarize { title, text, images? } → { line, points, cached }
 *   images : 본문 이미지 주소(포털 업로드 파일만). 가정통신문처럼 본문이 이미지뿐인 글은 이미지 속 글자를
 *            먼저 읽어 낸 뒤 본문과 합쳐 요약합니다.
 *   line   : 게시판 목록에 붙는 한 줄 요약
 *   points : 글 화면 위에 보이는 자세한 요약(글머리표)
 *
 * GET /weather → 서울특별시 날씨(현재·시간별 3일·일별 7일). 귀가 기간 홈·위젯 안내용. 30분 KV 에 보관해 모든 사용자에게
 *   나눠 줍니다. 기상청 단기예보 조회서비스(공공데이터포털, 서울 중구 소공동=서울시청 격자 60,127)만 씁니다 — 현재는
 *   초단기실황, 6시간은 초단기예보, 그 뒤는 단기예보(+3일까지 1시간, +4·5일은 3시간 간격). Open-Meteo 는 기상청 호출이
 *   실패하거나 키(KMA_SERVICE_KEY, 공공데이터포털 일반 인증키)가 없을 때만 통째로 대신 씁니다. 날씨는 WMO 코드로
 *   바꿔 보내 앱은 제공처를 몰라도 됩니다.
 *
 * 키 앞의 v3 는 프롬프트 버전 — 프롬프트를 바꾸면 올려서 예전 요약 대신 새로 만들게 합니다.
 * 요약은 (제목 + 본문) 해시마다 딱 한 번만 AI 로 만들고 KV 에 보관합니다. 같은 글을 다른 사용자가 열면
 * 저장된 요약을 돌려줄 뿐이라 AI 사용량이 늘지 않습니다. 키가 본문 해시라서 누가 엉뚱한 본문을 보내도
 * 진짜 글의 요약을 덮어쓸 수 없습니다.
 */

export interface Env {
  AI: Ai;
  SUMMARIES: KVNamespace;
  /** 공공데이터포털 '기상청_단기예보 조회서비스' 일반 인증키 — `wrangler secret put KMA_SERVICE_KEY`. 없으면 Open-Meteo 만. */
  KMA_SERVICE_KEY?: string;
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
    if (url.pathname === "/weather") return weather(req, env);
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

// MARK: - 날씨

/** 서울특별시청 — 위경도(Open-Meteo)와 기상청 격자(60,127). 사용자 위치는 받지 않습니다. */
const SEOUL = { lat: 37.5665, lon: 126.978, nx: 60, ny: 127 };
const WEATHER_KEY = "weather:seoul:v3";
const WEATHER_SECONDS = 30 * 60;

interface Hour { t: string; temp: number | null; code: number | null; pop: number | null }
interface Day { date: string; code: number | null; max: number | null; min: number | null; pop: number | null }
interface Forecast { source: string; current: { time: string; temp: number | null; code: number | null }; hourly: Hour[]; daily: Day[] }

async function weather(req: Request, env: Env): Promise<Response> {
  if (req.method !== "GET") return json({ error: "method" }, 405);
  if (req.headers.get("x-app") !== "hihana-neo") return json({ error: "forbidden" }, 403);
  const cached = await env.SUMMARIES.get(WEATHER_KEY);
  if (cached) return new Response(cached, { headers: { "content-type": "application/json; charset=utf-8" } });

  // 기상청만 씁니다. 기상청이 실패(또는 키 없음)할 때만 Open-Meteo 로 통째로 대신합니다 — 섞어 채우지 않습니다.
  let forecast: Forecast | null = null;
  if (env.KMA_SERVICE_KEY) {
    forecast = await kmaForecast(env.KMA_SERVICE_KEY).catch((e) => { console.log("kma failed", String(e)); return null; });
  }
  if (!forecast) forecast = await openMeteo().catch((e) => { console.log("open-meteo failed", String(e)); return null; });
  if (!forecast) return json({ error: "upstream" }, 502);

  // 대신 쓴 응답은 짧게(5분) 보관해 기상청이 돌아오면 곧 바뀌게 합니다.
  const body = JSON.stringify({ place: "서울", ...forecast, updated: new Date().toISOString() });
  await env.SUMMARIES.put(WEATHER_KEY, body, { expirationTtl: forecast.source === "kma" ? WEATHER_SECONDS : 5 * 60 });
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

async function openMeteo(): Promise<Forecast> {
  const api = new URL("https://api.open-meteo.com/v1/forecast");
  api.search = new URLSearchParams({
    latitude: String(SEOUL.lat),
    longitude: String(SEOUL.lon),
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

async function kmaCall(key: string, op: string, baseDate: string, baseTime: string): Promise<KmaItem[]> {
  const url = new URL(`${KMA_BASE}/${op}`);
  // 포털의 '인코딩' 키를 넣어도 되게 한 번 풀어서 다시 인코딩합니다.
  const serviceKey = key.includes("%") ? decodeURIComponent(key) : key;
  url.search = new URLSearchParams({
    // 단기예보는 +5일까지(요소 12종 × 시간) 라 넉넉히 한 번에 받습니다.
    serviceKey, pageNo: "1", numOfRows: "3000", dataType: "JSON",
    base_date: baseDate, base_time: baseTime, nx: String(SEOUL.nx), ny: String(SEOUL.ny),
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

async function kmaForecast(key: string): Promise<Forecast> {
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
    kmaCall(key, op, d, t).catch((e) => { console.log(`kma ${op} skipped`, String(e)); return [] as KmaItem[]; });
  const [ncst, ultra, vil, morning] = await Promise.all([
    optional("getUltraSrtNcst", ncstAt.ymd, `${pad(ncstAt.hour)}00`),
    optional("getUltraSrtFcst", fcstAt.ymd, `${pad(fcstAt.hour)}30`),
    kmaCall(key, "getVilageFcst", vilDate, `${pad(vilHour)}00`),
    // 오늘 최저·최고(TMN 06시, TMX 15시)는 그 시각이 지나면 최신 발표에서 빠지므로 오늘 02시 발표에서 가져옵니다.
    k.hour >= 3 ? kmaCall(key, "getVilageFcst", k.ymd, "0200").catch(() => [] as KmaItem[]) : Promise.resolve([] as KmaItem[]),
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
