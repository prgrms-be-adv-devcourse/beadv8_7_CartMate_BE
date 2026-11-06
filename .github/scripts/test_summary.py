import glob
import os
import sys
import xml.etree.ElementTree as ET

SLOWEST = 10


def load_suites():
    suites = []
    for path in sorted(glob.glob("*/build/test-results/test/*.xml")):
        module = path.split(os.sep)[0]
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        suites.append({
            "module": module,
            "name": root.get("name", "").rsplit(".", 1)[-1],
            "tests": int(root.get("tests", 0)),
            "failed": int(root.get("failures", 0)) + int(root.get("errors", 0)),
            "skipped": int(root.get("skipped", 0)),
            "time": float(root.get("time", 0)),
        })
    return suites


def main():
    suites = load_suites()
    print("## 테스트 요약")
    if not suites:
        print("\n테스트 결과 XML이 없다(컴파일 실패 등으로 테스트가 돌지 않았을 수 있음).")
        return 0

    print("\n| 모듈 | 클래스 | 테스트 | 실패 | 건너뜀 | 클래스 시간 합(초) |")
    print("|---|---:|---:|---:|---:|---:|")
    for module in sorted({s["module"] for s in suites}):
        rows = [s for s in suites if s["module"] == module]
        print(f"| {module} | {len(rows)} | {sum(s['tests'] for s in rows)} | {sum(s['failed'] for s in rows)} "
              f"| {sum(s['skipped'] for s in rows)} | {sum(s['time'] for s in rows):.1f} |")

    print(f"\n### 느린 테스트 클래스 상위 {SLOWEST}")
    print("\n| 모듈 | 클래스 | 시간(초) | 테스트 |")
    print("|---|---|---:|---:|")
    for s in sorted(suites, key=lambda s: s["time"], reverse=True)[:SLOWEST]:
        print(f"| {s['module']} | {s['name']} | {s['time']:.1f} | {s['tests']} |")

    failed = [s for s in suites if s["failed"]]
    if failed:
        print("\n### 실패한 클래스")
        for s in failed:
            print(f"- {s['module']} · {s['name']} ({s['failed']}건)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
