/*
 * 클래스룸 중계 — 학생 본인 권한으로 도는 Apps Script(classroom-bridge/Code.gs)가 올린 암호문을 기기가 받아 갑니다.
 *
 * 비밀키는 기기와 그 학생의 스크립트만 압니다. 여기엔 비밀키에서 만든 id 와 암호문만 있어 내용을 볼 수 없습니다.
 * KV 는 하루 쓰기 횟수가 적어(15분마다 올리는 학생이 많으면 넘침) D1 에 둡니다.
 *
 *   PUT    /bridge/<id>          스크립트 → 암호문 저장 (연결 해제된 id 면 410)
 *   GET    /bridge/<id>?ping=1   스크립트 → 내용이 그대로일 때 살아 있는지만 (없으면 404 → 다시 올림, 해제·방치면 410)
 *   GET    /bridge/<id>          앱 → 암호문
 *   DELETE /bridge/<id>          앱 → 연결 해제 (암호문을 지우고 표시만 남겨 다음 동기화에서 스크립트가 멈추게)
 *
 * 테이블: CREATE TABLE bridge (id TEXT PRIMARY KEY, data TEXT, updated INTEGER, seen INTEGER, gone INTEGER NOT NULL DEFAULT 0);
 */

import type { Env } from "./index";

const ID = /^[0-9a-f]{32}$/;
const MAX_BYTES = 1_500_000;
/** 앱이 이만큼 안 읽어 가면(앱을 지웠거나 안 씀) 스크립트를 멈추게 하고 지웁니다. */
const ABANDON_MS = 45 * 86_400_000;
/** 앱이 읽어 간 시각은 하루에 한 번만 적습니다 (D1 쓰기 절약). */
const SEEN_EVERY_MS = 86_400_000;

interface Row { data: string | null; updated: number | null; seen: number | null; gone: number }

export async function bridge(req: Request, env: Env, url: URL): Promise<Response> {
  if (req.headers.get("x-app") !== "hihana-neo") return reply(403);
  const id = url.pathname.slice("/bridge/".length);
  if (!ID.test(id)) return reply(404);
  const now = Date.now();
  const row = await env.BRIDGE.prepare("SELECT data, updated, seen, gone FROM bridge WHERE id = ?").bind(id).first<Row>();

  if (req.method === "PUT") {
    if (row?.gone) return reply(410);
    const data = await req.text();
    if (!data || data.length > MAX_BYTES || !/^[A-Za-z0-9+/=]+$/.test(data)) return reply(400);
    await env.BRIDGE.prepare(
      "INSERT INTO bridge (id, data, updated, seen) VALUES (?1, ?2, ?3, ?3) ON CONFLICT(id) DO UPDATE SET data = ?2, updated = ?3",
    ).bind(id, data, now).run();
    return reply(204);
  }

  if (req.method === "DELETE") {
    await env.BRIDGE.prepare(
      "INSERT INTO bridge (id, data, updated, seen, gone) VALUES (?1, NULL, ?2, ?2, 1) ON CONFLICT(id) DO UPDATE SET data = NULL, gone = 1",
    ).bind(id, now).run();
    return reply(204);
  }

  if (req.method !== "GET") return reply(405);
  if (!row) return reply(404);
  if (row.gone) return reply(410);

  if (url.searchParams.has("ping")) {
    if (now - (row.seen ?? 0) > ABANDON_MS) {
      await env.BRIDGE.prepare("UPDATE bridge SET data = NULL, gone = 1 WHERE id = ?").bind(id).run();
      return reply(410);
    }
    return reply(204);
  }

  if (now - (row.seen ?? 0) > SEEN_EVERY_MS) {
    await env.BRIDGE.prepare("UPDATE bridge SET seen = ? WHERE id = ?").bind(now, id).run();
  }
  if (!row.data) return reply(404);
  return new Response(JSON.stringify({ data: row.data, updated: row.updated }), {
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function reply(status: number): Response {
  return new Response(null, { status });
}
