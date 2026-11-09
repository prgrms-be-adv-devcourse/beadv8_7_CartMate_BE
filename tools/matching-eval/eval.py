import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.request
from itertools import combinations
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODEL = os.environ.get("EMBED_MODEL", "gemini-embedding-2")
KEY_FILE = Path.home() / ".config" / "cartmate" / "gemini.env"
CACHE = HERE / "embeddings-cache.json"
BATCH = 90

W0 = r"(?<![가-힣A-Za-z0-9])"
W1 = r"(?![가-힣A-Za-z0-9])"


def words(ws):
    return W0 + "(" + "|".join(re.escape(w) for w in ws) + ")" + W1


BRANDS = ["나이키", "아디다스", "푸마", "뉴발란스", "언더아머", "미즈노", "아식스", "엄브로", "데상트", "르꼬끄",
          "스타", "셀렉트", "몰텐", "스팔딩", "윌슨", "미카사",
          "머큐리얼", "프레데터", "코파", "팬텀", "티엠포", "페가수스"]
NOISE = [
    r"\([^)]*\)|\[[^]]*]",
    words(BRANDS),
    r"\d+(\.\d+)?\s*(호|ml|mL|L|l|mm|cm|kg|g|켤레|개입|개|벌|족|매|세트)" + W1,
    r"(?i:(사이즈|size)\s*\d+)" + W1,
    words(["XXS", "XS", "S", "M", "L", "XL", "XXL", "FG", "AG", "TF", "IC", "MG", "SG"]),
    W0 + r"\d{2,4}" + W1,
    words(["성인용", "성인", "아동용", "아동", "키즈", "어린이", "유아용", "유아", "초등학생",
           "저학년용", "고학년용", "주니어", "남성용", "여성용", "남성", "여성", "남녀공용"]),
    words(["무료", "인쇄", "마킹", "프린팅", "공식", "정품", "인증", "FIFA", "BPA", "프리",
           "이상", "가능", "포함", "일체형", "타입", "슬립인", "천연잔디용", "인조잔디", "실내외 겸용", "실내용", "실외용"]),
]
SYNONYMS_V1 = [("풋살공", "축구공"), ("풋살볼", "축구공"), ("풋살화", "축구화"), ("터프화", "축구화"),
               ("신가드", "정강이 보호대"), ("저지", "유니폼"), ("단체복", "단체 유니폼"),
               ("물통", "물병"), ("보틀", "물병"), ("워터저그", "물병")]
SYNONYMS_V2 = [s for s in SYNONYMS_V1 if s[0] not in ("풋살공", "풋살볼")]


def normalize(name, synonyms):
    text = name
    for p in NOISE:
        text = re.sub(p, " ", text)
    for a, b in synonyms:
        text = re.sub(words([a]), b, text)
    text = re.sub(r"\s+", " ", text).strip()
    return text or name.strip()


LABEL_MODELS = ["gemini-2.5-flash", "gemini-flash-lite-latest"]
LABEL_CACHE = HERE / "labels-cache.json"
LABEL_PROMPT = """각 상품의 '상품 종류'를 공동구매로 묶을 수 있는 수준의 짧은 한국어 명사구로 써라.
브랜드, 모델명, 크기, 용량, 연령(성인/아동), 색상, 홍보 문구는 빼라.
단, 구단 · 국가대표 유니폼은 구단 · 팀 이름을 남겨라(예: "레알 마드리드 유니폼").
풋살공과 축구공은 다른 종류다. 축구화는 바닥 종류(FG · TF · AG · HG · IC)와 상관없이 "축구화"로 쓴다.
응답은 입력 순서대로 문자열만 담은 JSON 배열.
상품:
%s"""


def labels(names):
    cache = json.loads(LABEL_CACHE.read_text()) if LABEL_CACHE.exists() else {}
    key = "\n".join(names)
    if key in cache:
        return cache[key]
    listing = "\n".join(f"{i + 1}. {n}" for i, n in enumerate(names))
    body = {"contents": [{"parts": [{"text": LABEL_PROMPT % listing}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0}}
    last = None
    for model in LABEL_MODELS:
        req = urllib.request.Request(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "x-goog-api-key": api_key()})
        try:
            with urllib.request.urlopen(req, timeout=120) as res:
                out = json.loads(json.load(res)["candidates"][0]["content"]["parts"][0]["text"])
            if len(out) == len(names):
                cache[key] = out
                LABEL_CACHE.write_text(json.dumps(cache, ensure_ascii=False))
                print(f"labeled {len(names)} names ({model})", file=sys.stderr)
                return out
        except urllib.error.HTTPError as e:
            last = e
    sys.exit(f"labeling failed: {last}")


VARIANTS = {
    "name": lambda n: n,
    "rule-v1": lambda n: normalize(n, SYNONYMS_V1),
    "rule-v2": lambda n: normalize(n, SYNONYMS_V2),
}


def api_key():
    for line in KEY_FILE.read_text().splitlines():
        if line.startswith("GEMINI_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit("GEMINI_API_KEY not found in " + str(KEY_FILE))


def embed(texts, cache):
    missing = [t for t in dict.fromkeys(texts) if f"{MODEL}\t{t}" not in cache]
    key = api_key() if missing else None
    for i in range(0, len(missing), BATCH):
        if i:
            time.sleep(62)
        chunk = missing[i:i + BATCH]
        body = {"requests": [{"model": f"models/{MODEL}", "content": {"parts": [{"text": t}]},
                              "taskType": "SEMANTIC_SIMILARITY"} for t in chunk]}
        req = urllib.request.Request(
            f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:batchEmbedContents",
            data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "x-goog-api-key": key})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=60) as res:
                    out = json.load(res)["embeddings"]
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or attempt == 2:
                    raise
                print("rate limited, waiting 65s", file=sys.stderr)
                time.sleep(65)
        if len(out) != len(chunk):
            sys.exit("embedding count mismatch")
        for t, e in zip(chunk, out):
            cache[f"{MODEL}\t{t}"] = e["values"]
        CACHE.write_text(json.dumps(cache))
        print(f"embedded {len(chunk)} texts ({MODEL})", file=sys.stderr)
    return {t: cache[f"{MODEL}\t{t}"] for t in texts}


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def scores(products, texts, vectors):
    rows = []
    for p, q in combinations(products, 2):
        if p["categoryId"] != q["categoryId"]:
            continue
        rows.append((cosine(vectors[texts[p["id"]]], vectors[texts[q["id"]]]), p["group"] == q["group"], p, q))
    return rows


def metrics(rows, t):
    tp = sum(1 for s, pos, *_ in rows if s >= t and pos)
    fp = sum(1 for s, pos, *_ in rows if s >= t and not pos)
    fn = sum(1 for s, pos, *_ in rows if s < t and pos)
    prec = tp / (tp + fp) if tp + fp else 1.0
    rec = tp / (tp + fn) if tp + fn else 1.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    return prec, rec, f1, tp, fp, fn


def main():
    products = json.loads((HERE / "products.json").read_text())
    cache = json.loads(CACHE.read_text()) if CACHE.exists() else {}
    out = [f"# 유사도 기준값 측정 ({MODEL}, 같은 카테고리 사전 필터)", "",
           f"상품 {len(products)}개, 같은 카테고리 쌍만 비교. 정답 = 같은 group.", ""]
    all_texts = {name: {p["id"]: fn(p["name"]) for p in products} for name, fn in VARIANTS.items()}
    llm = labels([p["name"] for p in products])
    all_texts["llm"] = {p["id"]: label.strip() or p["name"] for p, label in zip(products, llm)}
    vectors = embed([t for texts in all_texts.values() for t in texts.values()], cache)
    best = {}
    for name in all_texts:
        texts = all_texts[name]
        rows = scores(products, texts, vectors)
        grid = [round(0.60 + i * 0.01, 2) for i in range(36)]
        results = [(t, *metrics(rows, t)) for t in grid]
        top = max(results, key=lambda r: (r[3], r[1]))
        best[name] = (top, rows, texts)
        out += [f"## {name}", "", f"쌍 {len(rows)}개(정답 {sum(1 for r in rows if r[1])}개). 최고 F1 {top[3]:.3f} @ {top[0]:.2f} "
                f"(정밀도 {top[1]:.3f} · 재현율 {top[2]:.3f})", "",
                "| 기준값 | 정밀도 | 재현율 | F1 | TP | FP | FN |", "|---|---|---|---|---|---|---|"]
        out += [f"| {t:.2f} | {p:.3f} | {r:.3f} | {f:.3f} | {tp} | {fp} | {fn} |"
                for t, p, r, f, tp, fp, fn in results if round(t * 100) % 2 == 0]
        out.append("")
    name = max(best, key=lambda k: best[k][0][3])
    (t, *_), rows, texts = best[name]
    out += [f"## 가장 좋은 변형({name}) @ {t:.2f}의 오답", "", "### 잘못 묶음(FP)", ""]
    out += [f"- {s:.3f} {p['name']} ↔ {q['name']} (`{texts[p['id']]}` ↔ `{texts[q['id']]}`)"
            for s, pos, p, q in sorted(rows, key=lambda r: -r[0]) if s >= t and not pos]
    out += ["", "### 놓침(FN)", ""]
    out += [f"- {s:.3f} {p['name']} ↔ {q['name']} (`{texts[p['id']]}` ↔ `{texts[q['id']]}`)"
            for s, pos, p, q in sorted(rows, key=lambda r: r[0]) if s < t and pos]
    (HERE / "results.md").write_text("\n".join(out) + "\n")
    print("\n".join(out[:4]))
    for k, ((t, p, r, f, *_), _, _) in best.items():
        print(f"{k}: F1 {f:.3f} @ {t:.2f} (P {p:.3f} R {r:.3f})")


if __name__ == "__main__":
    main()
