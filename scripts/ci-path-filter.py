#!/usr/bin/env python3
"""Classify PR paths for selective GitHub Actions checks.

Input: NUL-delimited file paths from 'git diff --name-only --no-renames -z'.
Unknown build/application paths deliberately run BOTH suites (fail closed).
"""
from __future__ import annotations

import os
from pathlib import Path
import sys

BACKEND = "backend"
FRONTEND = "frontend"
BOTH = frozenset((BACKEND, FRONTEND))
NONE = frozenset()


def classify_one(path: str) -> frozenset[str]:
    p = path.lstrip("./")
    name = p.rsplit("/", 1)[-1]

    # Integration tests exercise server-rendered HTML. A template change may
    # break rendering or browser behavior, so it must run both suites.
    if p.startswith("src/main/resources/templates/"):
        return BOTH

    if p.startswith(("src/main/java/", "src/test/java/", "src/test/resources/")):
        return frozenset((BACKEND,))
    if p.startswith(("src/main/resources/db/", "src/main/resources/application")):
        return frozenset((BACKEND,))
    if p.startswith(("src/main/resources/static/", "uploads/chemistry/")):
        return frozenset((FRONTEND,))

    if p in ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
             "gradlew", "gradlew.bat", "pom.xml"):
        return frozenset((BACKEND,))
    if p.startswith("gradle/"):
        return frozenset((BACKEND,))

    if p in ("package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock"):
        return frozenset((FRONTEND,))
    if p.startswith("scripts/css/"):
        return frozenset((FRONTEND,))
    if p.startswith("scripts/"):
        if p in ("scripts/ci-path-filter.py",):
            return BOTH  # Changing routing logic must be exercised end-to-end.
        if p.startswith("scripts/tests/"):
            if name.endswith(".browser.cjs"):
                return BOTH  # Many browser tests need Java-generated HTML.
            if name.endswith(".test.cjs") or name.endswith(".test.js"):
                return frozenset((FRONTEND,))
            if name in ("deployment-static.test.sh",):
                return NONE
            return BOTH
        if p in ("scripts/setup-css.sh", "scripts/tailwind-cli.cjs",
                 "scripts/check-css-drift.cjs") or name.startswith("build-") and name.endswith(".cjs"):
            return frozenset((FRONTEND,))
        if p in ("scripts/deploy-pi-app.sh", "scripts/retire-pi-dev.sh"):
            return NONE
        return BOTH

    # CI workflow edits change the merge gate itself; verify both paths.
    if p == ".github/workflows/ci-tests.yml":
        return BOTH
    if p.startswith(".github/"):
        return NONE
    if p in ("Dockerfile", ".dockerignore", "docker-compose.yml"):
        return BOTH
    if p == "qodana.yaml":
        return frozenset((BACKEND,))

    if (name.endswith(".md") or p.startswith(("docs/", ".cursor/", ".vscode/",
           ".githooks/")) or p in (".env.example", ".gitignore",
           ".gitattributes", "LICENSE", "LICENSE.txt")):
        return NONE

    # Unknown files must not bypass validation silently.
    return BOTH


def classify(paths: list[str]) -> dict[str, str]:
    enabled = set()
    for path in paths:
        if path:
            enabled.update(classify_one(path))
    if not paths:
        enabled.update(BOTH)
    backend = BACKEND in enabled
    frontend = FRONTEND in enabled
    return {
        "backend": str(backend).lower(),
        "frontend": str(frontend).lower(),
        "integration": str(backend and frontend).lower(),
        "qodana": str(backend).lower(),
    }


def run_self_tests() -> None:
    scenarios = {
        "backend-only Java": (["src/main/java/com/example/App.java"], ("true", "false")),
        "backend-only DB": (["src/main/resources/db/migration/V12.sql"], ("true", "false")),
        "frontend-only CSS": (["src/main/resources/static/css/site.css"], ("false", "true")),
        "frontend-only JS": (["src/main/resources/static/js/app.js"], ("false", "true")),
        "frontend-only build": (["package-lock.json", "scripts/build-css.cjs"], ("false", "true")),
        "frontend-only tests": (["scripts/tests/objekt-transfers.test.cjs"], ("false", "true")),
        "mixed files": (["src/main/java/A.java", "src/main/resources/static/css/a.css"], ("true", "true")),
        "Mustache template": (["src/main/resources/templates/main.mustache"], ("true", "true")),
        "cross-layer browser fixture": (["scripts/tests/objekt-modal.browser.cjs"], ("true", "true")),
        "CI workflow": ([".github/workflows/ci-tests.yml"], ("true", "true")),
        "Dockerfile": (["Dockerfile"], ("true", "true")),
        "unknown app path": (["src/main/resources/other/config"], ("true", "true")),
        "docs only": (["README.md", "docs/DEPLOYMENT.md"], ("false", "false")),
        "prod deployment scripts": (["scripts/deploy-pi-app.sh", ".github/workflows/deploy-main.yml"], ("false", "false")),
        "empty": ([], ("true", "true")),
        "rename backend to frontend": (["src/main/java/A.java", "src/main/resources/static/a.js"], ("true", "true")),
    }
    for name, (paths, expected) in scenarios.items():
        actual = classify(paths)
        got = (actual["backend"], actual["frontend"])
        assert got == expected, f"{name}: expected {expected}, got {got}"
        assert actual["integration"] == str(all(v == "true" for v in got)).lower()
        assert actual["qodana"] == actual["backend"]
    print(f"PASS: {len(scenarios)} change-routing scenarios")


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        run_self_tests()
        return
    if len(sys.argv) != 2 or sys.argv[1] != "--stdin-z":
        raise SystemExit("Usage: ci-path-filter.py --stdin-z | --self-test")
    raw = sys.stdin.buffer.read()
    paths = [os.fsdecode(x) for x in raw.split(b"\x00") if x]
    result = classify(paths)
    for path in paths:
        print(f"  {path} => {','.join(sorted(classify_one(path))) or 'config-only'}")
    print("Selected checks:", ", ".join(f"{k}={v}" for k, v in result.items()))
    if destination := os.environ.get("GITHUB_OUTPUT"):
        with Path(destination).open("a", encoding="utf-8") as out:
            for key, value in result.items():
                out.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
