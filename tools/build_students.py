#!/usr/bin/env python3
"""
Google 주소록 디렉터리 페이지(Cmd+A 복사 텍스트) → 앱 내장 학번→이름 표.

  python3 tools/build_students.py ~/Downloads/directory.txt

- 학생은 이메일이 has_<학번>@hana.hs.kr 이라 그 줄 바로 앞의 이름과 짝지어 모읍니다 (교사 등 다른 메일은 건너뜀).
- 결과는 AES-256-GCM 으로 잠가 TimeTableAndroid/app/src/debug/assets/students.bin 에 씁니다(dev 빌드 전용). 키는 앱 안의 상수에서
  파생되므로 진짜 비밀은 아니고(APK 를 뜯어 파일을 그대로 열어 보는 정도만 막음), 앱은 학사시스템 계정을 등록한
  사용자에게만 풉니다.
"""
import hashlib, json, os, re, secrets, sys

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.exit("pip3 install cryptography 가 필요합니다")

SECRET = "hihana-neo-students-v1"   # StudentDirectory.kt 의 상수와 같아야 합니다

def parse(text: str) -> dict:
    lines = [l.strip() for l in text.splitlines()]
    result = {}
    for i, line in enumerate(lines):
        m = re.fullmatch(r"has_(\d{5})@hana\.hs\.kr", line, re.I)
        if not m:
            continue
        # 바로 위의 비어 있지 않은 줄이 이름 (아이콘 이름 'drag_indicator' 등은 건너뜀)
        j = i - 1
        while j >= 0 and (not lines[j] or lines[j] in ("drag_indicator", "연락처 저장")):
            j -= 1
        if j < 0:
            continue
        name = lines[j]
        if re.search(r"[A-Za-z_@]", name) or len(name) > 6:
            continue
        result[m.group(1)] = name
    return result

def main():
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/Downloads/directory.txt")
    text = open(src, encoding="utf-8").read()
    table = parse(text)
    print(f"학생 {len(table)}명 추출")
    plain = json.dumps(table, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    key = hashlib.sha256(SECRET.encode()).digest()
    nonce = secrets.token_bytes(12)
    sealed = AESGCM(key).encrypt(nonce, plain, None)
    # 디버그(dev) 빌드에만 넣습니다 — src/debug/assets 는 릴리스 APK 에 포함되지 않습니다. 파일은 .gitignore 로 커밋 제외.
    out = os.path.join(os.path.dirname(__file__), "..", "TimeTableAndroid", "app", "src", "debug", "assets", "students.bin")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as f:
        f.write(nonce + sealed)
    print(f"→ {os.path.abspath(out)} ({len(plain)} bytes 평문, {len(sealed)+12} bytes 저장)")

if __name__ == "__main__":
    main()
