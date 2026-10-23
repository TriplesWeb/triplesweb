#!/usr/bin/env python3
"""Remove uploads not referenced in src/seed/git (and optional DB)."""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
UPLOAD_ROOT = ROOT / "uploads"

# 레거시 정적 자료 (코드·시드에 경로 없음)
LEGACY_DIRS = [
    "images/케미 리스트",
    "images/상단",
    "images/하단",
    "images/상징로고",
    "images/objekts",
]

# 시드에서 ASCII 경로로 대체된 유니코드 파일명
LEGACY_FILES = [
    "images/ↀ.jpg",
    "images/⟡.jpg",
    "images/performante.jpg",
    "images/beyondbeauty.jpg",
    "images/structure.jpg",
    "images/touch.jpg",
    "images/banner1.jpg",
    "images/lightstick.jpg",
    "images/mainbannercomback.jpg",
    "images/triples2.jpg",
    "images/1465199f-7088-412b-9bd2-9b828f4e13cb.jpg",
    "images/f7f46699-83f9-4640-9e0a-034ccdb903d6.jpg",
]


def norm_rel(path: str) -> str:
    p = unquote(path.strip().replace("\\", "/"))
    if "/uploads/" in p:
        p = p.split("/uploads/", 1)[1]
    elif p.startswith("uploads/"):
        p = p[len("uploads/") :]
    return p.lstrip("/")


def collect_refs() -> set[str]:
    refs: set[str] = set()
    pat = re.compile(r"/uploads/[^\s\"'\\)<>\]]+")
    for base in [ROOT / "src", ROOT / "README.md"]:
        files = [base] if base.is_file() else base.rglob("*")
        for fp in files:
            if not fp.is_file():
                continue
            if fp.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".mp4"}:
                continue
            try:
                text = fp.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for m in pat.findall(text):
                refs.add(norm_rel(m.rstrip(".,;)'\"")))

    seed = ROOT / "src/main/resources/data/chemistry-seed.json"
    if seed.exists():
        data = json.loads(seed.read_text(encoding="utf-8"))
        for item in data.get("pairs", []):
            if item.get("imageUrl"):
                refs.add(norm_rel(item["imageUrl"]))

    out = subprocess.run(["git", "ls-files", "uploads"], capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():
        if line.strip():
            refs.add(norm_rel(line))
    return refs


def main() -> int:
    dry = "--dry-run" in sys.argv
    refs = collect_refs()
    removed = 0

    for rel in LEGACY_DIRS + LEGACY_FILES:
        p = UPLOAD_ROOT / rel
        if not p.exists():
            continue
        if dry:
            print("would remove", p.relative_to(ROOT))
        else:
            if p.is_dir():
                import shutil

                shutil.rmtree(p)
            else:
                p.unlink()
        removed += 1

    for p in UPLOAD_ROOT.rglob("*"):
        if not p.is_file():
            continue
        if p.name == ".DS_Store" or p.suffix == ".orig":
            if dry:
                print("would remove", p.relative_to(ROOT))
            else:
                p.unlink()
            removed += 1

    print(f"{'would remove' if dry else 'removed'} {removed} paths (legacy dirs/files + .orig/.DS_Store)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
