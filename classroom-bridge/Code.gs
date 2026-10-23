/*
 * 하이하나 Neo — 구글 클래스룸 중계 (Apps Script 웹 앱).
 *
 * 학교 계정은 만 18세 미만이라 외부 앱의 Google 로그인(OAuth)이 막혀 있지만, 같은 학교 계정이 만든 Apps Script 는 내부 앱으로
 * 허용됩니다. 그래서 앱 대신 이 스크립트가 학생 본인 권한으로 클래스룸을 읽습니다.
 *
 * 흐름
 *  1. 앱이 이 웹 앱을 브라우저로 엽니다: …/exec?k=<기기 비밀키>&p=<앱 패키지>. 학생이 권한을 허용하면 doGet 이
 *     비밀키를 그 학생의 사용자 속성에 적고, 15분마다 도는 트리거를 그 학생 이름으로 만들고, 바로 한 번 동기화합니다.
 *  2. sync() 가 수업·공지·과제(+내 제출)·자료를 모아 gzip → 비밀키로 암호화(HMAC-SHA256 CTR + HMAC 태그) 해서
 *     중계 서버(summary-worker /bridge/<id>)에 올립니다. 서버는 암호문만 보관하고 내용은 볼 수 없습니다.
 *     내용이 그대로면 다시 올리지 않고 살아 있는지만 묻습니다.
 *  3. 앱이 연결을 끊거나(서버가 410) 한 달 넘게 안 읽으면 그 비밀키를 지우고, 남은 키가 없으면 트리거도 지웁니다.
 *
 * 배포: 배포 › 배포 관리 › (연필) › 버전: 새 버전 — '새 배포'를 만들면 주소가 바뀌어 앱의 SCRIPT_URL 도 바꿔야 합니다.
 * 설정: 실행 계정 '웹 앱에 액세스하는 사용자', 액세스 '하나고등학교의 모든 사용자'. 서비스에 Google Classroom API(v1).
 */

const WORKER = 'https://hihana-summary.kyoohan0711ultra.workers.dev/bridge/';
const MAX_DEVICES = 3;
const EVERY_MINUTES = 15;

// MARK: - 웹 앱

function doGet(e) {
  const params = (e && e.parameter) || {};
  const secret = params.k;
  const pkg = String(params.p || 'com.skh.hihanaevo').replace(/[^\w.]/g, '');
  if (!secret || !/^[A-Za-z0-9_-]{43}=?$/.test(secret)) {
    return page('앱에서 연결해 주세요', '하이하나 Neo › 설정 › 게시판 › Google 클래스룸에서 연결을 누르면 이 화면이 다시 열립니다.', null);
  }
  const props = PropertiesService.getUserProperties();
  const secrets = devices().filter(s => s !== secret).concat([secret]).slice(-MAX_DEVICES);
  props.setProperty('secrets', JSON.stringify(secrets));
  ensureTrigger();
  const back = 'intent://classroom#Intent;scheme=hihananeo;package=' + pkg + ';end';
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
    '</style><main><small>하이하나 Neo · 클래스룸</small><h1>' + esc(title) + '</h1><p>' + esc(body) + '</p>' +
    (back ? '<a href="' + esc(back) + '" target="_top">앱으로 돌아가기</a>' : '') +
    '</main>';
  return HtmlService.createHtmlOutput(html)
    .setTitle('하이하나 Neo · 클래스룸')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

// MARK: - 동기화

function sync() {
  const list = devices();
  if (!list.length) { stopTrigger(); return; }
  const content = JSON.stringify(collect());
  const hash = hex(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, content, Utilities.Charset.UTF_8));
  const props = PropertiesService.getUserProperties();
  const plain = Utilities.gzip(Utilities.newBlob(content.slice(0, -1) + ',"at":' + Date.now() + '}', 'application/json')).getBytes();
  const keep = [];
  list.forEach(secret => {
    const keys = derive(secret);
    const sentKey = 'h_' + keys.id;
    const opts = { muteHttpExceptions: true, headers: { 'x-app': 'hihana-neo', 'x-hash': hash } };
    let res;
    if (props.getProperty(sentKey) === hash) {
      // 내용이 그대로면 살아 있는지만 — 404(서버에서 지워짐)면 아래에서 다시 올립니다.
      res = UrlFetchApp.fetch(WORKER + keys.id + '?ping=1', opts);
    }
    if (!res || res.getResponseCode() === 404) {
      opts.method = 'put';
      opts.contentType = 'text/plain';
      opts.payload = seal(plain, keys);
      res = UrlFetchApp.fetch(WORKER + keys.id, opts);
      if (res.getResponseCode() < 300) props.setProperty(sentKey, hash);
    }
    if (res.getResponseCode() === 410) props.deleteProperty(sentKey);
    else keep.push(secret);
  });
  props.setProperty('secrets', JSON.stringify(keep));
  if (!keep.length) stopTrigger();
}

/** 수업마다 공지·과제·내 제출·자료. 한 수업이 실패해도 나머지는 보냅니다. 시각은 API 그대로(UTC) — 앱이 한국 시각으로 바꿉니다. */
function collect() {
  const courses = Classroom.Courses.list({ studentId: 'me', courseStates: ['ACTIVE'], pageSize: 100 }).courses || [];
  const posts = [];
  courses.forEach(c => {
    attempt(() => (Classroom.Courses.Announcements.list(c.id, { pageSize: 20, orderBy: 'updateTime desc' }).announcements || [])
      .forEach(a => posts.push(post(a, c.id, 'A', null))));
    const subs = {};
    attempt(() => (Classroom.Courses.CourseWork.StudentSubmissions.list(c.id, '-', { userId: 'me', pageSize: 100 }).studentSubmissions || [])
      .forEach(s => { subs[s.courseWorkId] = s; }));
    attempt(() => (Classroom.Courses.CourseWork.list(c.id, { pageSize: 30, orderBy: 'updateTime desc' }).courseWork || [])
      .forEach(w => posts.push(post(w, c.id, 'W', subs[w.id] || {}))));
    attempt(() => (Classroom.Courses.CourseWorkMaterials.list(c.id, { pageSize: 15, orderBy: 'updateTime desc' }).courseWorkMaterial || [])
      .forEach(m => posts.push(post(m, c.id, 'M', null))));
  });
  return {
    v: 1,
    email: Session.getActiveUser().getEmail(),
    courses: courses.map(c => ({ id: c.id, name: c.name || '', section: c.section || null, link: c.alternateLink || null })),
    posts: posts,
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

function ensureTrigger() {
  if (!ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'sync')) {
    ScriptApp.newTrigger('sync').timeBased().everyMinutes(EVERY_MINUTES).create();
  }
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
