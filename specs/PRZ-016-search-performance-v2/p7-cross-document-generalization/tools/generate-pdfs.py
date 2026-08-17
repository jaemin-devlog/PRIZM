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
from reportlab.platypus import KeepTogether, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "dataset" / "pdf-sources"
OUTPUT = ROOT / "dataset" / "documents"
FONT_REGULAR = Path(r"C:\Windows\Fonts\malgun.ttf")
FONT_BOLD = Path(r"C:\Windows\Fonts\malgunbd.ttf")


def register_fonts() -> None:
    if not FONT_REGULAR.exists() or not FONT_BOLD.exists():
        raise RuntimeError("Required Korean fonts are unavailable")
    pdfmetrics.registerFont(TTFont("PRIZM-Regular", str(FONT_REGULAR)))
    pdfmetrics.registerFont(TTFont("PRIZM-Bold", str(FONT_BOLD)))


def footer(canvas, doc) -> None:
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#D7DEE8"))
    canvas.line(18 * mm, 14 * mm, A4[0] - 18 * mm, 14 * mm)
    canvas.setFont("PRIZM-Regular", 7.5)
    canvas.setFillColor(colors.HexColor("#667085"))
    canvas.drawString(18 * mm, 9.5 * mm, "PRIZM P7 · SYNTHETIC EVALUATION ASSET · NOT A REAL PERSON")
    canvas.drawRightString(A4[0] - 18 * mm, 9.5 * mm, f"PAGE {doc.page}")
    canvas.restoreState()


def build(source_path: Path) -> Path:
    data = json.loads(source_path.read_text(encoding="utf-8"))
    output_path = OUTPUT / f"{source_path.stem}.pdf"
    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=17 * mm,
        bottomMargin=19 * mm,
        title=data["title"],
        author="PRIZM P7 synthetic dataset",
        subject="Synthetic holdout document",
        creator="PRIZM P7 deterministic source generator",
    )

    base = getSampleStyleSheet()
    title = ParagraphStyle(
        "Title",
        parent=base["Title"],
        fontName="PRIZM-Bold",
        fontSize=22,
        leading=28,
        textColor=colors.HexColor("#172B4D"),
        alignment=TA_LEFT,
        spaceAfter=4 * mm,
    )
    subtitle = ParagraphStyle(
        "Subtitle",
        parent=base["Normal"],
        fontName="PRIZM-Regular",
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#596780"),
        spaceAfter=6 * mm,
    )
    heading = ParagraphStyle(
        "Heading",
        parent=base["Heading2"],
        fontName="PRIZM-Bold",
        fontSize=11.5,
        leading=15,
        textColor=colors.HexColor("#0B6E75"),
        spaceBefore=2.5 * mm,
        spaceAfter=1.5 * mm,
    )
    body = ParagraphStyle(
        "Body",
        parent=base["BodyText"],
        fontName="PRIZM-Regular",
        fontSize=9.4,
        leading=15,
        textColor=colors.HexColor("#25324B"),
        spaceAfter=2.2 * mm,
    )
    metric_value = ParagraphStyle(
        "MetricValue",
        parent=body,
        fontName="PRIZM-Bold",
        fontSize=12,
        leading=15,
        textColor=colors.HexColor("#172B4D"),
        alignment=TA_LEFT,
    )
    metric_label = ParagraphStyle(
        "MetricLabel",
        parent=body,
        fontSize=7.5,
        leading=10,
        textColor=colors.HexColor("#667085"),
        alignment=TA_LEFT,
    )

    story = [Paragraph(data["title"], title), Paragraph(data["subtitle"], subtitle)]

    metrics = data.get("metrics", [])
    if metrics:
        cells = []
        for value, label in metrics:
            cells.append([Paragraph(value, metric_value), Paragraph(label, metric_label)])
        metric_table = Table([cells], colWidths=[(A4[0] - 36 * mm) / len(cells)] * len(cells))
        metric_table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F3F6FA")),
                    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#D7DEE8")),
                    ("INNERGRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D7DEE8")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 8),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                    ("TOPPADDING", (0, 0), (-1, -1), 7),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                ]
            )
        )
        story.extend([metric_table, Spacer(1, 4 * mm)])

    for section in data["sections"]:
        block = [Paragraph(section["heading"], heading)]
        block.extend(Paragraph(text, body) for text in section["paragraphs"])
        story.append(KeepTogether(block))

    OUTPUT.mkdir(parents=True, exist_ok=True)
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    return output_path


def main() -> int:
    register_fonts()
    source_paths = sorted(SOURCES.glob("*.json"))
    if len(source_paths) != 4:
        raise RuntimeError(f"Expected 4 PDF sources, found {len(source_paths)}")
    outputs = [build(path) for path in source_paths]
    for output in outputs:
        print(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
