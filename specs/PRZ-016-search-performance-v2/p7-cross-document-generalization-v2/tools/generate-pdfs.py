from __future__ import annotations

import json
import sys
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "dataset" / "pdf-sources"
OUTPUT = ROOT / "dataset" / "documents"
FONT_REGULAR = Path(r"C:\Windows\Fonts\malgun.ttf")
FONT_BOLD = Path(r"C:\Windows\Fonts\malgunbd.ttf")
NAVY = colors.HexColor("#17345B")
TEAL = colors.HexColor("#087E78")
INK = colors.HexColor("#29364D")
MUTED = colors.HexColor("#66758E")
PALE = colors.HexColor("#F2F6FA")
LINE = colors.HexColor("#D7E0EA")


def register_fonts() -> None:
    if not FONT_REGULAR.exists() or not FONT_BOLD.exists():
        raise RuntimeError("Required Korean fonts are unavailable")
    pdfmetrics.registerFont(TTFont("P7-Regular", str(FONT_REGULAR)))
    pdfmetrics.registerFont(TTFont("P7-Bold", str(FONT_BOLD)))


def decorate_page(canvas, doc) -> None:
    canvas.saveState()
    canvas.setStrokeColor(LINE)
    canvas.line(17 * mm, 14 * mm, A4[0] - 17 * mm, 14 * mm)
    canvas.setFont("P7-Regular", 7.2)
    canvas.setFillColor(MUTED)
    canvas.drawString(17 * mm, 9.5 * mm, "PRIZM P7-A v2  |  SYNTHETIC RESUME  |  NOT A REAL PERSON")
    canvas.drawRightString(A4[0] - 17 * mm, 9.5 * mm, f"PAGE {doc.page} / 2")
    canvas.restoreState()


def styles():
    base = getSampleStyleSheet()
    return {
        "name": ParagraphStyle("Name", parent=base["Title"], fontName="P7-Bold", fontSize=20, leading=24, textColor=NAVY, alignment=TA_LEFT, spaceAfter=1.5 * mm),
        "role": ParagraphStyle("Role", parent=base["Normal"], fontName="P7-Bold", fontSize=10.5, leading=14, textColor=TEAL, spaceAfter=1.5 * mm),
        "notice": ParagraphStyle("Notice", parent=base["Normal"], fontName="P7-Regular", fontSize=7.5, leading=10, textColor=MUTED, spaceAfter=3 * mm),
        "summary": ParagraphStyle("Summary", parent=base["BodyText"], fontName="P7-Regular", fontSize=8.8, leading=13.6, textColor=INK, spaceAfter=3 * mm),
        "page_label": ParagraphStyle("PageLabel", parent=base["Normal"], fontName="P7-Bold", fontSize=8, leading=11, textColor=TEAL, spaceAfter=2.5 * mm),
        "heading": ParagraphStyle("Heading", parent=base["Heading2"], fontName="P7-Bold", fontSize=10.2, leading=13, textColor=NAVY, spaceBefore=1.8 * mm, spaceAfter=0.5 * mm),
        "meta": ParagraphStyle("Meta", parent=base["Normal"], fontName="P7-Bold", fontSize=7.4, leading=10, textColor=TEAL, spaceAfter=1.1 * mm),
        "body": ParagraphStyle("Body", parent=base["BodyText"], fontName="P7-Regular", fontSize=8.5, leading=13.2, textColor=INK, spaceAfter=1.2 * mm),
        "bullet": ParagraphStyle("Bullet", parent=base["BodyText"], fontName="P7-Regular", fontSize=8.3, leading=12.4, textColor=INK, leftIndent=4 * mm, firstLineIndent=-2.5 * mm, bulletIndent=0, spaceAfter=0.6 * mm),
        "metric_value": ParagraphStyle("MetricValue", parent=base["Normal"], fontName="P7-Bold", fontSize=10.5, leading=13, textColor=NAVY),
        "metric_label": ParagraphStyle("MetricLabel", parent=base["Normal"], fontName="P7-Regular", fontSize=6.8, leading=8.5, textColor=MUTED),
    }


def metric_table(metrics, style):
    cells = [[Paragraph(value, style["metric_value"]), Paragraph(label, style["metric_label"])] for value, label in metrics]
    table = Table([cells], colWidths=[(A4[0] - 34 * mm) / len(cells)] * len(cells))
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PALE),
        ("BOX", (0, 0), (-1, -1), 0.5, LINE),
        ("INNERGRID", (0, 0), (-1, -1), 0.35, LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return table


def section_flowables(section, style):
    items = [Paragraph(section["heading"], style["heading"]), Paragraph(section["meta"], style["meta"])]
    items.extend(Paragraph(text, style["body"]) for text in section.get("paragraphs", []))
    items.extend(Paragraph(f"• {text}", style["bullet"]) for text in section.get("bullets", []))
    return KeepTogether(items)


def build(source_path: Path) -> Path:
    data = json.loads(source_path.read_text(encoding="utf-8"))
    output_path = OUTPUT / f"{source_path.stem}.pdf"
    style = styles()
    doc = SimpleDocTemplate(
        str(output_path), pagesize=A4,
        leftMargin=17 * mm, rightMargin=17 * mm,
        topMargin=15 * mm, bottomMargin=19 * mm,
        title=f"{data['name']} - {data['role']}",
        author="PRIZM P7-A v2 synthetic dataset",
        subject="Synthetic two-page resume",
        creator="PRIZM P7-A v2 ReportLab generator",
    )
    story = []
    for page_index, page in enumerate(data["pages"]):
        if page_index == 0:
            story.extend([
                Paragraph(data["name"], style["name"]),
                Paragraph(data["role"], style["role"]),
                Paragraph(data["notice"], style["notice"]),
                Paragraph(data["summary"], style["summary"]),
                metric_table(data["metrics"], style),
                Spacer(1, 3 * mm),
            ])
        else:
            story.extend([
                Paragraph(f"{data['name']}  /  {data['role']}", style["role"]),
                Paragraph(data["notice"], style["notice"]),
            ])
        story.append(Paragraph(page["label"], style["page_label"]))
        for section in page["sections"]:
            story.append(section_flowables(section, style))
        if page_index < len(data["pages"]) - 1:
            story.append(PageBreak())
    OUTPUT.mkdir(parents=True, exist_ok=True)
    doc.build(story, onFirstPage=decorate_page, onLaterPages=decorate_page)
    return output_path


def main() -> int:
    register_fonts()
    sources = sorted(SOURCES.glob("*.json"))
    if len(sources) != 4:
        raise RuntimeError(f"Expected 4 PDF sources, found {len(sources)}")
    for source in sources:
        print(build(source))
    return 0


if __name__ == "__main__":
    sys.exit(main())
