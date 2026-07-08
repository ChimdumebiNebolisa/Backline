#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Running full test suite with JaCoCo reports"
./gradlew test >/tmp/backline-audit-test.log 2>&1 || {
  echo "Test suite failed. See /tmp/backline-audit-test.log"
  exit 1
}

echo "==> Summarizing JUnit results"
python3 - <<'PY'
import glob,xml.etree.ElementTree as ET
files=glob.glob('/workspace/**/build/test-results/test/*.xml',recursive=True)
T=F=E=S=0
for f in files:
    r=ET.parse(f).getroot()
    T+=int(r.attrib.get('tests',0));F+=int(r.attrib.get('failures',0));E+=int(r.attrib.get('errors',0));S+=int(r.attrib.get('skipped',0))
print(f'test_xml_files={len(files)} total_tests={T} failures={F} errors={E} skipped={S} pass={T-F-E-S}')
PY

echo "==> Summarizing JaCoCo coverage"
python3 - <<'PY'
import glob,xml.etree.ElementTree as ET
paths=sorted(glob.glob('/workspace/**/build/reports/jacoco/test/jacocoTestReport.xml',recursive=True))
print(f'coverage_reports={len(paths)}')
for p in paths:
    root=ET.parse(p).getroot()
    c={e.attrib['type']:(int(e.attrib['missed']),int(e.attrib['covered'])) for e in root.findall('counter')}
    missed,covered=c.get('LINE',(0,0)); total=missed+covered; pct=(covered/total*100 if total else 0)
    bmiss,bcov=c.get('BRANCH',(0,0)); btot=bmiss+bcov; bpct=(bcov/btot*100 if btot else 0)
    mod=p.split('/workspace/')[1].split('/build/')[0]
    print(f'{mod}: line={covered}/{total} ({pct:.1f}%) branch={bcov}/{btot} ({bpct:.1f}%)')
PY

echo "==> Runtime smoke (sample API)"
SESSION_NAME="audit-sample-api"
tmux has-session -t "=$SESSION_NAME" 2>/dev/null || tmux new-session -d -s "$SESSION_NAME" -c "$PWD" -- "${SHELL:-bash}" -l
tmux send-keys -t "$SESSION_NAME:0.0" './gradlew :apps:sample-api:bootRun' C-m
sleep 8
if curl -fsS "http://localhost:8081/health" >/tmp/backline-audit-sample-health.json 2>/dev/null; then
  echo "sample_api_health=$(cat /tmp/backline-audit-sample-health.json)"
else
  echo "Sample API health check failed"
fi
tmux send-keys -t "$SESSION_NAME:0.0" C-c
tmux kill-session -t "$SESSION_NAME" 2>/dev/null || true

echo "==> API runtime preflight (requires postgres on localhost:5432)"
if (echo >/dev/tcp/127.0.0.1/5432) >/dev/null 2>&1; then
  echo "Port 5432 responds. Run ./gradlew :apps:api:bootRun manually for full API runtime check."
else
  echo "Postgres not detected on localhost:5432; API boot smoke skipped."
fi

echo "Audit complete."
