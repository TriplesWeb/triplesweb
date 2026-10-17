#!/usr/bin/env python3
"""PROJECT_STRUCTURE.md → PDF (fpdf2 + mermaid.ink 다이어그램)."""
from __future__ import annotations

import base64
import re
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "PROJECT_STRUCTURE.md"
OUT = ROOT / "PROJECT_STRUCTURE.pdf"
# AppleGothic lacks OS/2 table (fpdf2 fails); Arial Unicode covers Hangul.
FONT = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"
FONT_BOLD = FONT

MERMAID = re.compile(r"```mermaid\n(.*?)```", re.DOTALL)
FENCE = re.compile(r"```(\w*)\n(.*?)```", re.DOTALL)
HEADING = re.compile(r"^(#{1,4})\s+(.*)$")
TABLE_SEP = re.compile(r"^\|[\s\-:|]+\|$")
HR = re.compile(r"^---+$")
IMG_PLACEHOLDER = re.compile(r"\[\[MERMAID_IMG:(\d+)\]\]")


def fetch_mermaid_png(code: str, dest: Path) -> bool:
    payload = base64.urlsafe_b64encode(code.strip().encode("utf-8")).decode("ascii")
    url = f"https://mermaid.ink/img/{payload}?type=png&bgColor=white"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "triplesweb-docs/1.0"})
        with urllib.request.urlopen(req, timeout=45) as resp:
            dest.write_bytes(resp.read())
        return dest.stat().st_size > 100
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"warn: mermaid render failed: {e}", file=sys.stderr)
        return False


def extract_and_replace_mermaid(md: str, img_dir: Path) -> tuple[str, list[Path]]:
    images: list[Path] = []

    def repl(m: re.Match) -> str:
        idx = len(images)
        path = img_dir / f"mermaid_{idx}.png"
        code = m.group(1)
        print(f"  mermaid diagram #{idx + 1}…")
        if fetch_mermaid_png(code, path):
            images.append(path)
            return f"\n[[MERMAID_IMG:{idx}]]\n"
        # fallback: keep as fenced code
        return f"\n```\n{code.strip()}\n```\n"

    return MERMAID.sub(repl, md), images


class DocPDF(FPDF):
    def footer(self) -> None:
        self.set_y(-12)
        self.set_font("Gothic", size=8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 8, f"{self.page_no()}", align="C")


def clean_inline(text: str) -> str:
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = text.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
    return text


def write_wrapped(pdf: DocPDF, text: str, size: int = 10, style: str = "") -> None:
    pdf.set_font("Gothic", style=style, size=size)
    pdf.set_text_color(26, 26, 26)
    pdf.multi_cell(0, size * 0.55 + 2, text)
    pdf.ln(1)


def write_code(pdf: DocPDF, code: str) -> None:
    pdf.set_fill_color(244, 246, 248)
    pdf.set_font("Gothic", size=8)
    pdf.set_text_color(40, 40, 40)
    # multi_cell with fill
    x = pdf.get_x()
    pdf.multi_cell(0, 4.2, code.rstrip() + "\n", fill=True)
    pdf.ln(2)


def write_table(pdf: DocPDF, rows: list[list[str]]) -> None:
    if not rows:
        return
    usable = pdf.epw
    cols = max(len(r) for r in rows)
    # normalize
    norm = [r + [""] * (cols - len(r)) for r in rows]
    # column widths by max char weight
    weights = [1] * cols
    for r in norm:
        for i, c in enumerate(r):
            weights[i] = max(weights[i], min(len(c) + 2, 40))
    total_w = sum(weights)
    widths = [usable * w / total_w for w in weights]

    pdf.set_font("Gothic", size=8)
    line_h = 5
    for ri, row in enumerate(norm):
        if ri == 0:
            pdf.set_fill_color(232, 244, 240)
            pdf.set_font("Gothic", size=8)
        else:
            pdf.set_fill_color(255, 255, 255)
            pdf.set_font("Gothic", size=8)
        # estimate row height
        max_lines = 1
        for i, cell in enumerate(row):
            # rough wrap estimate
            max_lines = max(max_lines, max(1, int(len(cell) / max(widths[i] / 2.2, 1)) + 1))
        row_h = line_h * min(max_lines, 6)
        if pdf.get_y() + row_h > pdf.page_break_trigger:
            pdf.add_page()
        y0 = pdf.get_y()
        x0 = pdf.l_margin
        for i, cell in enumerate(row):
            pdf.set_xy(x0 + sum(widths[:i]), y0)
            pdf.multi_cell(widths[i], line_h, cell, border=1, fill=True, max_line_height=line_h)
        pdf.set_y(y0 + row_h)
    pdf.ln(2)


def parse_table_row(line: str) -> list[str]:
    parts = [p.strip() for p in line.strip().strip("|").split("|")]
    return [clean_inline(p) for p in parts]


def render(md: str, images: list[Path]) -> None:
    pdf = DocPDF(format="A4")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_font("Gothic", fname=FONT)
    pdf.add_font("Gothic", style="B", fname=FONT_BOLD)
    pdf.add_page()
    pdf.set_title("tripleS Web — 프로젝트 구조 문서")

    lines = md.splitlines()
    i = 0
    in_code = False
    code_buf: list[str] = []
    table_buf: list[list[str]] = []

    def flush_table() -> None:
        nonlocal table_buf
        if table_buf:
            write_table(pdf, table_buf)
            table_buf = []

    while i < len(lines):
        line = lines[i]

        # mermaid image placeholder
        m_img = IMG_PLACEHOLDER.search(line.strip())
        if m_img:
            flush_table()
            idx = int(m_img.group(1))
            if idx < len(images) and images[idx].exists():
                if pdf.get_y() > 200:
                    pdf.add_page()
                pdf.ln(2)
                # fit width
                pdf.image(str(images[idx]), w=min(pdf.epw, 180))
                pdf.ln(4)
            i += 1
            continue

        if line.startswith("```") and not in_code:
            flush_table()
            in_code = True
            code_buf = []
            i += 1
            continue
        if line.startswith("```") and in_code:
            write_code(pdf, "\n".join(code_buf))
            in_code = False
            code_buf = []
            i += 1
            continue
        if in_code:
            code_buf.append(line)
            i += 1
            continue

        if line.strip().startswith("|") and "|" in line.strip()[1:]:
            if TABLE_SEP.match(line.strip()):
                i += 1
                continue
            table_buf.append(parse_table_row(line))
            i += 1
            # peek: if next is not table, flush
            if i >= len(lines) or not lines[i].strip().startswith("|"):
                flush_table()
            continue
        else:
            flush_table()

        if HR.match(line.strip()) or line.strip() == "---":
            pdf.ln(2)
            pdf.set_draw_color(200, 200, 200)
            pdf.line(pdf.l_margin, pdf.get_y(), pdf.l_margin + pdf.epw, pdf.get_y())
            pdf.ln(4)
            i += 1
            continue

        hm = HEADING.match(line)
        if hm:
            level = len(hm.group(1))
            title = clean_inline(hm.group(2))
            sizes = {1: 18, 2: 14, 3: 12, 4: 11}
            if level <= 2 and pdf.get_y() > 250:
                pdf.add_page()
            pdf.ln(3 if level <= 2 else 2)
            write_wrapped(pdf, title, size=sizes.get(level, 11), style="B")
            if level <= 2:
                pdf.set_draw_color(0, 168, 144)
                pdf.set_line_width(0.4 if level == 1 else 0.2)
                pdf.line(pdf.l_margin, pdf.get_y(), pdf.l_margin + pdf.epw, pdf.get_y())
                pdf.ln(3)
            i += 1
            continue

        if line.strip().startswith("> "):
            write_wrapped(pdf, clean_inline(line.strip()[2:]), size=9)
            i += 1
            continue

        if re.match(r"^[-*]\s+", line.strip()) or re.match(r"^\d+\.\s+", line.strip()):
            write_wrapped(pdf, "• " + clean_inline(re.sub(r"^([-*]|\d+\.)\s+", "", line.strip())), size=10)
            i += 1
            continue

        if not line.strip():
            pdf.ln(2)
            i += 1
            continue

        write_wrapped(pdf, clean_inline(line), size=10)
        i += 1

    flush_table()
    pdf.output(str(OUT))


def main() -> int:
    if not Path(FONT).exists():
        print(f"error: Korean font not found: {FONT}", file=sys.stderr)
        return 1
    raw = SRC.read_text(encoding="utf-8")
    with tempfile.TemporaryDirectory() as tmp:
        img_dir = Path(tmp)
        print("rendering mermaid diagrams…")
        processed, images = extract_and_replace_mermaid(raw, img_dir)
        print("building PDF…")
        render(processed, images)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
