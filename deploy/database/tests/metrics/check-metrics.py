#!/usr/bin/env python3
"""仅对本机后端执行 A4 统计接口口径断言。"""
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlencode, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError(f"拒绝跳转：HTTP {code}")


def require_local(base):
    parsed = urlparse(base)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
        raise ValueError("A4_BACKEND_URL 只能是 localhost、127.0.0.1 或 ::1 的 HTTP(S) 地址")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("A4_BACKEND_URL 只允许本机地址和端口")
    return base.rstrip("/")


EXPECTED = json.loads(Path(__file__).with_name("expected.json").read_text(encoding="utf-8"))
BASE = require_local(os.environ.get("A4_BACKEND_URL", "http://127.0.0.1:48080"))
ADMIN_TOKEN = os.environ.get("A4_ADMIN_TOKEN", "")
STUDENT_TOKEN = os.environ.get("A4_STUDENT_TOKEN", "")
if not ADMIN_TOKEN or not STUDENT_TOKEN:
    sys.exit("需设置 A4_ADMIN_TOKEN 和 A4_STUDENT_TOKEN（学生二 910002 的令牌）")

opener = build_opener(NoRedirect())
failures = []


def get(path, token, **params):
    suffix = "?" + urlencode(params) if params else ""
    req = Request(BASE + path + suffix, headers={"Authorization": "Bearer " + token})
    with opener.open(req, timeout=10) as response:
        payload = json.load(response)
    if payload.get("code") != 0:
        raise RuntimeError(f"{path}: 接口返回 code={payload.get('code')}，msg={payload.get('msg')}")
    return payload["data"]


def check(label, actual, expected):
    if actual != expected:
        failures.append(f"{label}: 实际 {actual!r}，期望 {expected!r}")


scope = EXPECTED["fixture"]
rates = get("/edu/statistics/total-pass-rate", ADMIN_TOKEN,
            classId=scope["validClassId"], courseId=scope["validCourseId"], academicYear=2025)
for key, expected in EXPECTED["totalPassRate"].items():
    if key != "calculation":
        check("总体分档." + key, rates.get(key), expected)

page = get("/edu/homework-record/page", ADMIN_TOKEN,
           homeworkId=scope["boundaryHomeworkId"], pageNo=1, pageSize=20)
rank_by_record = {str(item["id"]): item.get("classRank") for item in page["list"]}
for record_id, expected in EXPECTED["boundaryRanks"].items():
    if record_id != "calculation":
        check("并列名次." + record_id, rank_by_record.get(record_id), expected)

report = get("/edu/student/capacity/report/basic", STUDENT_TOKEN)
for key, expected in EXPECTED["studentTwoReport"].items():
    if key != "calculation":
        check("学生报告." + key, report.get(key), expected)

dashboard = get("/edu/statistics/admin/dashboard", ADMIN_TOKEN)
if dashboard.get("aiCorrections", 0) < EXPECTED["aiCorrectionsFixtureContribution"]:
    failures.append("AI 批改数未计入成功草案对应的已批改记录")

if failures:
    print("A4 口径检查失败：")
    print("\n".join("- " + item for item in failures))
    sys.exit(1)
print("A4 口径检查通过：分档、并列名次、学生报告、AI 批改数")
