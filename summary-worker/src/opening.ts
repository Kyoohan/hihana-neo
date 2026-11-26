// 첫 실행 오프닝 영상 — assets/opening/ 의 파일을 내려줍니다. 앱은 기기에 저장하지 않고 스트리밍으로 재생합니다.
// 아이폰 재생기(AVPlayer)는 영상을 구간(Range)으로 나눠 요청하고, 구간 요청이 안 되는 서버의 영상은 재생하지 않습니다.
// 정적 파일은 구간 요청을 무시하고 통째로 주므로, 여기서 받아 잘라 206 으로 돌려줍니다(파일이 몇 MB 라 통째로 읽어도 됨).
// 파일 이름에 버전이 들어가므로(opening-v1.mp4) 내용이 바뀌지 않음 → 오래 캐시.
import type { Env } from "./index";

const NAME = /^[a-z0-9][a-z0-9._-]{0,63}\.mp4$/;

export async function opening(req: Request, env: Env, url: URL): Promise<Response> {
  if (req.method !== "GET" && req.method !== "HEAD") return new Response("method", { status: 405 });
  const key = url.pathname.slice("/opening/".length);
  if (!NAME.test(key)) return new Response("not found", { status: 404 });

  const asset = await env.ASSETS.fetch(new Request(new URL(`/opening/${key}`, url.origin)));
  if (!asset.ok) return new Response("not found", { status: 404 });
  const buf = await asset.arrayBuffer();
  const size = buf.byteLength;
  const h = new Headers({
    "content-type": "video/mp4",
    "accept-ranges": "bytes",
    "cache-control": "public, max-age=31536000, immutable",
  });
  const etag = asset.headers.get("etag");
  if (etag) h.set("etag", etag);
  if (etag && req.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers: h });

  const range = parseRange(req.headers.get("range"), size);
  if (range === "bad") {
    h.set("content-range", `bytes */${size}`);
    return new Response(null, { status: 416, headers: h });
  }
  if (range) {
    const [start, end] = range;
    h.set("content-range", `bytes ${start}-${end}/${size}`);
    h.set("content-length", String(end - start + 1));
    return new Response(req.method === "HEAD" ? null : buf.slice(start, end + 1), { status: 206, headers: h });
  }
  h.set("content-length", String(size));
  return new Response(req.method === "HEAD" ? null : buf, { status: 200, headers: h });
}

/** "bytes=a-b" / "bytes=a-" / "bytes=-n" 하나만. 없으면 null(전체), 범위 밖이면 "bad". 여러 구간 요청은 전체로 돌려줍니다. */
function parseRange(v: string | null, size: number): [number, number] | null | "bad" {
  if (!v) return null;
  const m = /^bytes=(\d*)-(\d*)$/.exec(v.trim());
  if (!m || (m[1] === "" && m[2] === "")) return null;
  let start: number, end: number;
  if (m[1] === "") {
    const n = Number(m[2]);
    if (n === 0) return "bad";
    start = Math.max(0, size - n); end = size - 1;
  } else {
    start = Number(m[1]);
    end = m[2] === "" ? size - 1 : Math.min(Number(m[2]), size - 1);
  }
  if (start >= size || start > end) return "bad";
  return [start, end];
}
