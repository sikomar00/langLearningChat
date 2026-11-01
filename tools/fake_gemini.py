#!/usr/bin/env python3
"""에뮬레이터 화면 시험용 가짜 Gemini 서버.

앱의 GeminiAiService가 보내는 요청(responseSchema의 속성 이름)을 보고 그럴듯한 JSON을 돌려준다.
실제 AI가 아니므로 번역 품질은 보지 않고, 화면 흐름(번역·첨삭·채점·확장)만 확인하는 데 쓴다.

사용법:
  python3 tools/fake_gemini.py 8765
  adb -s emulator-5554 shell "run-as com.chatlearn sh -c 'echo http://10.0.2.2:8765 > files/ai_base_url.txt'"
  (앱을 다시 시작하면 디버그 빌드만 이 주소를 쓴다. API 키 칸에는 아무 값이나 넣는다. 예: FAKE-KEY)
"""
import json
import re
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

ES_HINT = re.compile(r"[ñáéíóúü¿¡]|\b(hola|qué|que|gracias|estás|vamos|mañana|bien|por favor|nos vemos|quieres)\b", re.I)


def lang_of(text):
    return "es" if ES_HINT.search(text) else "en"


def between(prompt, start="<<<\n", end="\n>>>"):
    i = prompt.find(start)
    if i < 0:
        return prompt
    j = prompt.find(end, i + len(start))
    return prompt[i + len(start): j if j >= 0 else None].strip()


def json_array_in(prompt):
    for line in reversed(prompt.splitlines()):
        line = line.strip()
        if line.startswith("[") and line.endswith("]"):
            try:
                return json.loads(line)
            except ValueError:
                pass
    m = re.search(r"\[(?:.|\n)*?\]", prompt)
    return json.loads(m.group(0)) if m else []


def key_phrase(text):
    words = re.findall(r"[\wñáéíóúü']+", text, re.I)
    return " ".join(words[:2]) if len(words) >= 3 else ""


def alt_of(text, lang):
    return ("(ES) " if lang == "en" else "(EN) ") + text


def received(text):
    lang = lang_of(text)
    alt = alt_of(text, lang)
    return {
        "detectedLang": lang, "koTranslation": f"(가짜 번역) {text}",
        "note": "가짜 서버의 설명이에요. 실제 앱에서는 표현과 말투를 알려 줘요.",
        "altText": alt, "keyPhrase": key_phrase(text), "altKeyPhrase": key_phrase(alt),
    }


def norm(s):
    return re.sub(r"[^\wñáéíóúü ]", "", s.lower()).split()


def similarity(a, b):
    a, b = norm(a), norm(b)
    if not a and not b:
        return 1.0
    same = sum(1 for x in a if x in b)
    return same / max(len(a), len(b))


def corrected_version(text):
    t = text.strip()
    fixes = [(r"\bi\b", "I"), (r"\bim\b", "I'm"), (r"\bdont\b", "don't"), (r"\bcant\b", "can't"),
             (r"\bwanna\b", "want to"), (r"\bgonna\b", "going to")]
    for pat, rep in fixes:
        t = re.sub(pat, rep, t)
    if t and t[0].islower():
        t = t[0].upper() + t[1:]
    if t and t[-1] not in ".!?":
        t += "."
    return t


def answer(prompt, props):
    keys = set(props.keys())
    if "results" in keys:
        items = json_array_in(prompt)
        return {"results": [received(x) for x in items]}
    if "candidates" in keys:
        ko = between(prompt)
        # 영어 요청에도 altText 설명에 "in Spanish"가 들어가므로 후보 언어 문구로 가린다.
        target = "es" if "natural Spanish options" in prompt else "en"
        base = "Nos vemos mañana" if target == "es" else "See you tomorrow"
        return {"candidates": [
            {"text": f"{base}!", "toneNote": f"편한 말투예요. (요청: {ko})", "altText": "See you tomorrow!" if target == "es" else "¡Nos vemos mañana!",
             "keyPhrase": base.split()[0] + " " + base.split()[1], "altKeyPhrase": "", "usedPhrase": ""},
            {"text": f"{base}, take care." if target == "en" else f"{base}, cuídate.", "toneNote": "조금 더 다정한 말투예요.",
             "altText": "", "keyPhrase": "take care" if target == "en" else "cuídate", "altKeyPhrase": "", "usedPhrase": ""},
        ]}
    if "verdict" in keys:
        ref = re.search(r"Reference answer: <<<(.*?)>>>", prompt, re.S)
        got = re.search(r"Learner's answer: <<<(.*?)>>>", prompt, re.S)
        ref, got = (ref.group(1) if ref else ""), (got.group(1) if got else "")
        s = similarity(ref, got)
        verdict = "correct" if s >= 0.95 else "close" if s >= 0.6 else "wrong"
        return {"verdict": verdict, "corrected": ref, "explanationKo": f"가짜 채점이에요. 일치도 {round(s * 100)}%."}
    if "corrected" in keys and "alternatives" in keys:
        text = between(prompt)
        lang = lang_of(text)
        fixed = corrected_version(text)
        natural = fixed == text.strip()
        return {
            "detectedLang": lang, "corrected": fixed, "natural": natural,
            "explanationKo": "이미 자연스러워요." if natural else "문장 첫 글자는 대문자로 쓰고, 끝에 마침표를 찍어요. 'I'는 늘 대문자예요.",
            "koTranslation": f"(가짜 뜻) {fixed}", "alternatives": [f"Honestly, {fixed[0].lower() + fixed[1:]}" if fixed else ""],
            "keyPhrase": key_phrase(fixed), "altText": alt_of(fixed, lang), "altKeyPhrase": key_phrase(alt_of(fixed, lang)),
        }
    if "meaningKo" in keys:
        word = re.search(r'word "(.*?)"', prompt)
        w = word.group(1) if word else "?"
        return {"meaningKo": f"(가짜 뜻) {w}", "baseForm": w.lower(), "note": "가짜 서버의 단어 설명이에요."}
    if "phrases" in keys:
        return {"phrases": [key_phrase(x) for x in json_array_in(prompt)]}
    if "texts" in keys:
        to_es = "into natural Spanish" in prompt
        return {"texts": [("(ES) " if to_es else "(EN) ") + x for x in json_array_in(prompt)]}
    if "detectedLang" in keys:
        return received(between(prompt))
    if "text" in keys:
        return {"text": "(번역) " + between(prompt)}
    return {}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        prompt = body["contents"][0]["parts"][0]["text"]
        props = body.get("generationConfig", {}).get("responseSchema", {}).get("properties", {})
        out = answer(prompt, props)
        reply = {"candidates": [{"content": {"parts": [{"text": json.dumps(out, ensure_ascii=False)}]}}]}
        data = json.dumps(reply, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        sys.stderr.write(f"[fake-gemini] {sorted(props.keys())}\n")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
