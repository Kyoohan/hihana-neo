/*
 * 하이하나 Neo — 구글 클래스룸 중계 (Apps Script 웹 앱).
 *
 * 학교 계정은 만 18세 미만이라 외부 앱의 Google 로그인(OAuth)이 막혀 있지만, 같은 학교 계정이 만든 Apps Script 는 내부 앱으로
 * 허용됩니다. 그래서 앱 대신 이 스크립트가 학생 본인 권한으로 클래스룸을 읽습니다.
 *
 * 흐름
 *  1. 앱이 이 웹 앱을 브라우저로 엽니다: …/exec?k=<기기 비밀키>&p=<앱 패키지>. 학생이 권한을 허용하면 doGet 이
 *     비밀키를 그 학생의 사용자 속성에 적고, 5분마다 도는 트리거를 그 학생 이름으로 만들고, 바로 한 번 동기화합니다.
 *  2. sync() 가 수업·공지·과제(+내 제출)·자료를 모아 gzip → 비밀키로 암호화(HMAC-SHA256 CTR + HMAC 태그) 해서
 *     중계 서버(summary-worker /bridge/<id>)에 올립니다. 서버는 암호문만 보관하고 내용은 볼 수 없습니다.
 *     내용이 그대로여도 매번 올립니다 — 올린 시각이 앱의 'N분 전 동기화'이자 '스크립트가 아직 돈다'는 표시입니다.
 *  3. 앱이 연결을 끊거나(서버가 410) 한 달 넘게 안 읽으면 그 비밀키를 지우고, 남은 키가 없으면 트리거도 지웁니다.
 *
 * 배포: 배포 › 배포 관리 › (연필) › 버전: 새 버전 — '새 배포'를 만들면 주소가 바뀌어 앱의 SCRIPT_URL 도 바꿔야 합니다.
 * 설정: 실행 계정 '웹 앱에 액세스하는 사용자', 액세스 '하나고등학교의 모든 사용자'. 서비스에 Google Classroom API(v1).
 */

const WORKER = 'https://hihana-summary.kyoohan0711ultra.workers.dev/bridge/';
const MAX_DEVICES = 6;
// 15분 → 5분 — 앱에 새 글·과제가 '째깍째깍' 오도록.
const EVERY_MINUTES = 5;
// 이 웹 앱 주소 — 앱의 ClassroomBridge.SCRIPT_URL 과 같습니다. 트리거 안에서는 ScriptApp.getService().getUrl() 이 믿을 만하지 않아 적어 둡니다.
const SCRIPT_URL = 'https://script.google.com/a/macros/hana.hs.kr/s/AKfycbxGrulUZa_ezE-akmMgvEh3njF2YWj8XuHuoOUQMKnsKJEdHIpePj-3th3Vo6Rqi1LS/exec';
// 학생용 과제 권한(classroom.coursework.me.readonly)에 아직 동의하지 않은 학생 — 2026-11-10 appsscript.json 에 권한을 적기 전엔 Google 이
// 교사용 과제 권한을 골라, 그때 연결한 학생은 모든 수업에서 과제 목록이 403 입니다. 그런 학생에겐 앱에 reconnect 표시를 보내고,
// 그 표시를 모르는 예전 앱을 위해 클래스룸 목록 맨 위에 안내 글(NOTICE_ID)도 넣습니다 — 새 앱은 그 글을 숨기고 배너를 띄웁니다.
// 다시 허용하면 403 이 사라져 둘 다 저절로 없어집니다.
const NOTICE_ID = 'hihana-reconnect';

// MARK: - 웹 앱

function doGet(e) {
  const params = (e && e.parameter) || {};
  const secret = params.k;
  const pkg = String(params.p || 'com.skh.hihanaevo').replace(/[^\w.]/g, '');
  if (!secret || !/^[A-Za-z0-9_-]{43}=?$/.test(secret)) {
    // 안내 글의 링크로 왔으면(비밀키 없이) — 이 화면이 열렸다는 건 방금 새 권한까지 허용했다는 뜻이라, 이미 연결된 학생이면 바로 다시 받아 둡니다.
    if (devices().length) {
      ensureTrigger();
      const home = 'intent://classroom#Intent;scheme=hihananeo;end';
      try {
        sync();
        return page('과제까지 연결되었습니다', '앱으로 돌아가 클래스룸을 새로고침하면 과제가 보입니다. 안내 글은 곧 저절로 사라집니다.', home);
      } catch (err) {
        return page('허용은 되었지만 바로 받지 못했습니다', String(err && err.message || err) + ' — ' + EVERY_MINUTES + '분 안에 다시 시도합니다.', home);
      }
    }
    return page('앱에서 연결해 주세요', '하이하나 앱 설정의 Google 클래스룸에서 연결을 누르면 이 화면이 다시 열립니다.', null);
  }
  const props = PropertiesService.getUserProperties();
  const before = devices();
  const secrets = before.filter(s => s !== secret).concat([secret]).slice(-MAX_DEVICES);
  props.setProperty('secrets', JSON.stringify(secrets));
  // 기기 수를 넘겨 밀려난 기기는 서버 사본도 지웁니다(410) — 그 앱이 '다시 연결'을 띄우도록. 예전엔 조용히 옛 내용만 남았습니다.
  before.filter(s => secrets.indexOf(s) < 0).forEach(s => forget(s, props));
  ensureTrigger();
  // iPhone 앱은 p=ios — 인증 창(ASWebAuthenticationSession)이 hihananeo:// 로 가는 순간 닫히고 앱이 이어받습니다.
  const back = pkg === 'ios' ? 'hihananeo://classroom' : 'intent://classroom#Intent;scheme=hihananeo;package=' + pkg + ';end';
  try {
    sync();
    return page('연결되었습니다', '앱으로 돌아가면 클래스룸 공지와 과제가 보입니다. 이후에는 ' + EVERY_MINUTES + '분마다 저절로 새로 받습니다.', back);
  } catch (err) {
    return page('연결은 되었지만 첫 동기화에 실패했습니다', String(err && err.message || err) + ' — ' + EVERY_MINUTES + '분 안에 다시 시도합니다.', back);
  }
}

function page(title, body, back) {
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const html =
    '<!doctype html><meta charset="utf-8">' +
    '<style>' +
    'body{margin:0;font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;background:#f4f5f7;color:#16181d}' +
    'main{max-width:420px;margin:0 auto;padding:56px 24px}' +
    'small{color:#1e8e3e;font-weight:600;letter-spacing:.02em}' +
    'h1{font-size:22px;margin:8px 0 10px}' +
    'p{font-size:15px;line-height:1.6;color:#5b606b;margin:0 0 28px}' +
    'a{display:block;text-align:center;background:#1e8e3e;color:#fff;text-decoration:none;font-weight:600;padding:15px;border-radius:999px}' +
    '@media (prefers-color-scheme:dark){body{background:#101114;color:#eceef2}p{color:#9aa0ab}}' +
    '</style><main><small>하이하나 · 클래스룸</small><h1>' + esc(title) + '</h1><p>' + esc(body) + '</p>' +
    (back ? '<a href="' + esc(back) + '" target="_top">앱으로 돌아가기</a>' : '') +
    '</main>';
  return HtmlService.createHtmlOutput(html)
    .setTitle('하이하나 · 클래스룸')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

// MARK: - 동기화

function sync() {
  const list = devices();
  if (!list.length) { stopTrigger(); return; }
  // 예전 주기(15분)로 만들어진 트리거는 다음 실행 때 새 주기로 바꿔 둡니다 — 학생이 다시 연결하지 않아도.
  ensureTrigger();
  const content = JSON.stringify(collect());
  const props = PropertiesService.getUserProperties();
  const plain = Utilities.gzip(Utilities.newBlob(content.slice(0, -1) + ',"at":' + Date.now() + '}', 'application/json')).getBytes();
  const keep = [];
  list.forEach(secret => {
    const keys = derive(secret);
    const opts = { muteHttpExceptions: true, headers: { 'x-app': 'hihana-neo' } };
    // 먼저 이 기기 몫이 살아 있는지 — 앱이 연결을 끊었거나 한 달 넘게 안 읽었으면 410 이라 더 올리지 않습니다.
    let res = UrlFetchApp.fetch(WORKER + keys.id + '?ping=1', opts);
    if (res.getResponseCode() !== 410) {
      // 내용이 그대로여도 매번 올립니다 — 올린 시각(at)이 앱의 'N분 전 동기화'라서, 예전처럼 바뀔 때만 올리면 5분마다 확인해도
      // 새 글이 없던 동안은 '17분 전 동기화'처럼 보였습니다.
      opts.method = 'put';
      opts.contentType = 'text/plain';
      opts.payload = seal(plain, keys);
      res = UrlFetchApp.fetch(WORKER + keys.id, opts);
    }
    // 예전 '바뀔 때만 올리기'가 남긴 기록은 지웁니다.
    props.deleteProperty('h_' + keys.id);
    props.deleteProperty('t_' + keys.id);
    if (res.getResponseCode() !== 410) keep.push(secret);
  });
  props.setProperty('secrets', JSON.stringify(keep));
  if (!keep.length) stopTrigger();
}

/** 수업마다 공지·과제·내 제출·자료. 한 수업이 실패해도 나머지는 보냅니다. 시각은 API 그대로(UTC) — 앱이 한국 시각으로 바꿉니다. */
function collect() {
  const courses = Classroom.Courses.list({ studentId: 'me', courseStates: ['ACTIVE'], pageSize: 100 }).courses || [];
  const posts = [];
  let denied = 0;
  courses.forEach(c => {
    attempt(() => (Classroom.Courses.Announcements.list(c.id, { pageSize: 20, orderBy: 'updateTime desc' }).announcements || [])
      .forEach(a => posts.push(post(a, c.id, 'A', null))));
    // 과제 목록이 403(권한 없음)이면 제출 목록도 403 이라 묻지 않습니다 — 수업마다 경고 두 줄씩 쌓이던 로그도 한 줄로.
    let work = null;
    try {
      work = Classroom.Courses.CourseWork.list(c.id, { pageSize: 30, orderBy: 'updateTime desc' }).courseWork || [];
    } catch (err) {
      if (forbidden(err)) denied++; else console.warn(err);
    }
    if (work) {
      const subs = {};
      attempt(() => (Classroom.Courses.CourseWork.StudentSubmissions.list(c.id, '-', { userId: 'me', pageSize: 100 }).studentSubmissions || [])
        .forEach(s => { subs[s.courseWorkId] = s; }));
      work.forEach(w => posts.push(post(w, c.id, 'W', subs[w.id] || {})));
    }
    attempt(() => (Classroom.Courses.CourseWorkMaterials.list(c.id, { pageSize: 15, orderBy: 'updateTime desc' }).courseWorkMaterial || [])
      .forEach(m => posts.push(post(m, c.id, 'M', null))));
  });
  // 모든 수업에서 막혔으면 수업 탓이 아니라 이 학생이 과제 권한을 허용하지 않은 것.
  const reconnect = courses.length > 0 && denied === courses.length;
  if (reconnect) {
    console.info('과제 권한 없음 — 다시 연결 안내를 보냅니다');
    posts.push(notice(posts));
  }
  const out = {
    v: 1,
    email: Session.getActiveUser().getEmail(),
    courses: courses.map(c => ({ id: c.id, name: c.name || '', section: c.section || null, link: c.alternateLink || null })),
    posts: posts,
  };
  if (reconnect) out.reconnect = 'coursework';
  return out;
}

function forbidden(err) {
  return !!(err && ((err.details && err.details.code === 403) || /does not have permission/.test(String(err.message || err))));
}

/**
 * 예전 앱에 보일 다시 연결 안내 — 공지(A) 꼴이라 어느 버전이든 클래스룸 목록·홈 카드에 뜹니다. 수업은 없는 id 라 수업 이름 없이 보이고,
 * 시각은 가장 최근 글보다 1초 뒤라 맨 위에 오면서 내용은 매번 같아(다시 올리지 않음) 둡니다. 링크는 학교 계정 고르기 → 이 웹 앱(비밀키 없이)
 * — 열리기 전에 Google 이 새 권한을 묻고, doGet 이 바로 한 번 다시 받습니다.
 */
function notice(posts) {
  const latest = posts.reduce((m, p) => Math.max(m, Date.parse(p.up) || 0), 0);
  const at = new Date(latest ? latest + 1000 : Date.parse('2026-11-10T00:00:00Z')).toISOString();
  const link = 'https://accounts.google.com/AccountChooser?hd=hana.hs.kr&continue=' + encodeURIComponent(SCRIPT_URL);
  return {
    id: NOTICE_ID, c: 'hihana', k: 'A', title: null,
    text: '하이하나 안내 · 과제도 보려면 클래스룸을 다시 허용해 주세요\n\n' +
      '그동안 공지만 보이고 과제는 안 보였어요. 과제를 읽는 권한이 빠져 있었기 때문이에요. 이번에 고쳤는데, 새 권한은 한 번 직접 허용해 주셔야 해요.\n\n' +
      '아래 \'다시 허용하기\'를 눌러 학교 계정을 고르고 허용을 누르면 끝나요. 앱 설정의 Google 클래스룸에서 다시 연결해도 돼요.\n\n' +
      '허용하면 몇 분 안에 과제가 보이고, 이 안내는 저절로 사라져요.',
    link: link, cr: at, up: at,
    att: [{ t: '다시 허용하기', u: link }],
  };
}

function post(x, courseId, kind, sub) {
  const o = {
    id: x.id, c: courseId, k: kind,
    title: x.title || null, text: kind === 'A' ? (x.text || '') : (x.description || ''),
    link: x.alternateLink || null, cr: x.creationTime, up: x.updateTime,
    att: attachments(x.materials),
  };
  if (kind === 'W') {
    const d = x.dueDate, t = x.dueTime;
    o.due = d ? { y: d.year, m: d.month, d: d.day, h: t ? (t.hours || 0) : null, mi: t ? (t.minutes || 0) : null } : null;
    o.pts = x.maxPoints == null ? null : x.maxPoints;
    o.st = sub.state || null;
    o.late = !!sub.late;
    o.grade = sub.assignedGrade == null ? null : sub.assignedGrade;
  }
  return o;
}

function attachments(list) {
  return (list || []).map(m => {
    if (m.driveFile && m.driveFile.driveFile) return { t: m.driveFile.driveFile.title || '드라이브 파일', u: m.driveFile.driveFile.alternateLink };
    if (m.youtubeVideo) return { t: m.youtubeVideo.title || 'YouTube', u: m.youtubeVideo.alternateLink };
    if (m.link) return { t: m.link.title || m.link.url, u: m.link.url };
    if (m.form) return { t: m.form.title || '설문', u: m.form.formUrl };
    return null;
  }).filter(a => a && a.u);
}

function attempt(fn) {
  try { fn(); } catch (err) { console.warn(err); }
}

// MARK: - 기기·트리거

function devices() {
  try { return JSON.parse(PropertiesService.getUserProperties().getProperty('secrets') || '[]'); } catch (err) { return []; }
}

/** 목록에서 밀려난 기기 — 서버 사본을 지워(앱은 다음 읽기에서 410 → 다시 연결) 기억해 둔 해시·시각도 지웁니다. */
function forget(secret, props) {
  const keys = derive(secret);
  try {
    UrlFetchApp.fetch(WORKER + keys.id, { method: 'delete', muteHttpExceptions: true, headers: { 'x-app': 'hihana-neo' } });
  } catch (err) {
    console.warn(err);
  }
  props.deleteProperty('h_' + keys.id);
  props.deleteProperty('t_' + keys.id);
}

function ensureTrigger() {
  // 트리거는 주기를 읽을 수 없어, 만든 주기를 사용자 속성에 적어 두고 다르면 지우고 새로 만듭니다.
  const props = PropertiesService.getUserProperties();
  const current = ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'sync');
  if (current.length === 1 && props.getProperty('every') === String(EVERY_MINUTES)) return;
  current.forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('sync').timeBased().everyMinutes(EVERY_MINUTES).create();
  props.setProperty('every', String(EVERY_MINUTES));
}

function stopTrigger() {
  ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'sync').forEach(t => ScriptApp.deleteTrigger(t));
}

// MARK: - 암호화 (앱의 ClassroomBridge.open 과 짝)
//
// 비밀키 S(32바이트)에서 id = HMAC(S,"id") 앞 16바이트(hex), enc = HMAC(S,"enc"), mac = HMAC(S,"mac").
// 봉투 = nonce(16) ‖ 평문 XOR 키스트림 ‖ HMAC(mac, nonce ‖ 암호문). 키스트림 i번째 32바이트 = HMAC(enc, nonce ‖ i(빅엔디언 4바이트)).

function derive(secret) {
  const s = Utilities.base64DecodeWebSafe(secret.length === 43 ? secret + '=' : secret);
  const label = t => Utilities.newBlob(t).getBytes();
  return {
    id: hex(hmac(label('id'), s)).slice(0, 32),
    enc: hmac(label('enc'), s),
    mac: hmac(label('mac'), s),
  };
}

function seal(plain, keys) {
  const nonce = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, Utilities.getUuid() + Date.now()).slice(0, 16);
  const out = new Array(plain.length);
  for (let i = 0, block = 0; i < plain.length; i += 32, block++) {
    const ks = hmac(nonce.concat(be32(block)), keys.enc);
    for (let j = 0; j < 32 && i + j < plain.length; j++) out[i + j] = plain[i + j] ^ ks[j];
  }
  const tag = hmac(nonce.concat(out), keys.mac);
  return Utilities.base64Encode(nonce.concat(out, tag));
}

function hmac(value, key) {
  return Utilities.computeHmacSha256Signature(value, key);
}

function be32(n) {
  return [n >>> 24, (n >>> 16) & 255, (n >>> 8) & 255, n & 255].map(b => (b > 127 ? b - 256 : b));
}

function hex(bytes) {
  return bytes.map(b => ((b + 256) % 256).toString(16).padStart(2, '0')).join('');
}
