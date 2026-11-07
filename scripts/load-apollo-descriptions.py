#!/usr/bin/env python3
"""한글 번역본(ko)만 objekt_description에 넣는다. 영어 원문은 저장하지 않는다.

기존 설명이 비어 있거나, 영어 원문을 포함하거나, 한글보다 라틴 문자가 많으면 덮어쓴다.
이미 한글만 있는 관리자 설명은 유지한다.
"""
import json
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "data" / "apollo-objekt-descriptions.json"


def hangul_count(text: str) -> int:
    return len(re.findall(r"[가-힣]", text or ""))


def latin_count(text: str) -> int:
    return len(re.findall(r"[A-Za-z]", text or ""))


def should_replace(existing: str | None, en: str, ko: str) -> bool:
    current = (existing or "").strip()
    if not current:
        return True
    # 영어 원문이 그대로/일부 남아 있으면 한글로 교체
    if en and en in current:
        return True
    # 한글+영어 이중 표기(줄바꿈으로 영어가 붙은 경우)
    lines = [ln.strip() for ln in current.splitlines() if ln.strip()]
    if len(lines) >= 2 and hangul_count(lines[0]) and latin_count(lines[-1]) and hangul_count(lines[-1]) == 0:
        return True
    # 사실상 영어만 남아 있으면 교체
    if hangul_count(current) == 0 and latin_count(current) > 0:
        return True
    # 이미 같은 한글이면 스킵
    if current == ko:
        return False
    return False


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    entries = [e for e in seed.get("entries", []) if (e.get("ko") or "").strip()]
    print(f"loadable_ko={len(entries)} total={len(seed.get('entries', []))}")

    conn = pymysql.connect(
        host="100.89.171.59",
        port=3306,
        user="triples_dev",
        password="triples0213",
        database="triples",
        connect_timeout=10,
        charset="utf8mb4",
    )
    cur = conn.cursor()
    inserted = replaced = skipped = 0
    for entry in entries:
        key = entry["descriptionKey"]
        ko = entry["ko"].strip()
        en = (entry.get("en") or "").strip()
        # 안전: 번역 결과가 영어 원문과 같으면 넣지 않음
        if ko == en or hangul_count(ko) == 0:
            skipped += 1
            continue
        cur.execute(
            "SELECT id, description FROM objekt_description WHERE objekt_key=%s",
            (key,),
        )
        row = cur.fetchone()
        if row is None:
            cur.execute(
                "INSERT INTO objekt_description (objekt_key, description, updated_at) VALUES (%s, %s, NOW())",
                (key, ko),
            )
            inserted += 1
        elif should_replace(row[1], en, ko):
            cur.execute(
                "UPDATE objekt_description SET description=%s, updated_at=NOW() WHERE id=%s",
                (ko, row[0]),
            )
            replaced += 1
        else:
            skipped += 1
    conn.commit()
    cur.execute(
        'SELECT COUNT(*) FROM objekt_description WHERE description IS NOT NULL AND TRIM(description)<>""'
    )
    print(
        f"inserted={inserted} replaced={replaced} skipped={skipped} nonEmptyNow={cur.fetchone()[0]}"
    )
    # 샘플: 370z 키가 한글만인지
    cur.execute(
        "SELECT description FROM objekt_description WHERE objekt_key=%s",
        ("group-ec5b4e4a-52ca-3b7c-abda-e468e1bea058",),
    )
    row = cur.fetchone()
    print("sample370z", repr(row[0] if row else None))
    conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
