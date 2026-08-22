#!/usr/bin/env python3
"""Generate the deterministic PRIZM P16 realistic synthetic PDF fixture."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
)


class DeterministicCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        kwargs["invariant"] = 1
        super().__init__(*args, **kwargs)


def register_fonts() -> tuple[str, str]:
    regular = Path("C:/Windows/Fonts/malgun.ttf")
    bold = Path("C:/Windows/Fonts/malgunbd.ttf")
    if not regular.is_file() or not bold.is_file():
        raise FileNotFoundError("Required Malgun Gothic fonts were not found.")
    pdfmetrics.registerFont(TTFont("PrizmKorean", str(regular)))
    pdfmetrics.registerFont(TTFont("PrizmKoreanBold", str(bold)))
    return "PrizmKorean", "PrizmKoreanBold"


def paragraphs(section: dict[str, str]) -> list[str]:
    situation = section["situation"]
    pressure = section["pressure"]
    decision = section["decision"]
    verification = section["verification"]
    reflection = section["reflection"]
    embedded = section.get("embeddedSentence", "")

    first = (
        f"이번 구간에서 먼저 확인한 상황은 {situation}. 현장 기록을 한 문장으로 요약하면 "
        f"문제는 단일 기능의 결함보다 입력이 만들어진 시점과 담당 경계가 서로 달랐다는 데 있었고, "
        f"같은 현상을 본 사람도 자신이 맡은 단계만 설명해 전체 흐름을 재구성하기 어려웠다. 특히 {pressure}. "
        "그래서 회의에서 나온 해석을 곧바로 사실로 적지 않고 원문에 남은 표현, 시스템 상태, 재현 가능한 "
        "관찰과 아직 확인하지 못한 가정을 나눴다. 이 구분은 문서가 길어져도 유지했으며, 이후에 새로운 "
        "자료가 도착했을 때 기존 결론을 조용히 바꾸지 않고 어떤 근거가 추가되었는지 추적할 수 있게 했다."
    )
    second_prefix = (
        f"설계 단계에서는 {decision}. 이 선택을 적용할 때는 편리한 예외를 먼저 추가하지 않고 정상 흐름과 "
        "실패 흐름이 같은 소유권 및 상태 조건을 통과하도록 구성했다. 또한 처리 속도를 이유로 원본 확인을 "
        "생략하지 않았고, 캐시나 임시 표시가 최종 저장 상태의 근거가 되지 않게 책임을 분리했다. "
    )
    second = second_prefix + (f"{embedded} " if embedded else "") + (
        "변경 범위 밖의 동작은 그대로 두고 새 코드는 관찰하려는 질문에 필요한 최소 경로에만 배치했으며, "
        "오류가 발생하면 이전에 검증된 상태가 계속 사용되도록 했다. 구현 설명에는 성공 사례뿐 아니라 "
        "거부 조건과 되돌릴 수 있는 경계도 함께 남겨, 다음 사람이 결과만 보고 허용 범위를 확대 해석하지 "
        "않도록 했다."
    )
    third = (
        f"검증은 {verification}. 한 번의 성공 로그로 끝내지 않고 순서, 건수, 식별자와 source 위치를 함께 "
        "비교했으며 예상과 다른 값은 자동으로 통과시키지 않았다. 정상 입력 외에도 지연, 중복, 누락, "
        "다른 사용자, 이전 버전과 경계 문자열을 조합해 같은 계약이 유지되는지 살폈다. 검사 도구가 만든 "
        "요약과 실제 데이터베이스 행 또는 추출 원문을 표본으로 대조했고, 실행하지 못한 환경은 성공으로 "
        "간주하지 않고 NOT_RUN 또는 NOT_VERIFIED로 남겼다. 이 방식은 숫자가 좋아 보이는지보다 어떤 "
        "조건에서 그 숫자가 만들어졌는지 설명하는 데 초점을 맞췄다."
    )
    fourth = (
        f"회고에서 가장 중요하게 남긴 판단은 {reflection}. 짧은 데모에서는 드러나지 않던 경쟁 상태와 "
        "문서 경계 문제가 긴 입력과 반복 실행에서 나타났고, 이를 해결하는 과정에서 구현보다 검증 순서를 "
        "먼저 고정하는 편이 효과적이었다. 결과가 기대에 미치지 못한 경우에도 입력을 수정해 성공처럼 만들지 "
        "않고 실패 이유와 재개 조건을 기록했다. 이후 작업자는 이 기록을 근거로 같은 범위를 재현하거나 "
        "새로운 synthetic corpus에서 일반화 여부를 확인할 수 있으며, production 반영은 별도의 설계 검토와 "
        "승인을 거쳐야 한다."
    )
    fifth = (
        f"운영 관점에서 다시 살펴보면 {pressure}. 이 제약은 특정 라이브러리나 도구 하나로 사라지는 문제가 "
        "아니어서 담당자는 입력 수집, 상태 변경, 실패 복구, 사용자 응답의 책임자를 각각 명시했다. 문서에는 "
        "최종 선택뿐 아니라 검토했지만 제외한 선택과 제외 이유를 남겼고, 숫자로 측정하지 않은 개선은 성능 "
        "향상으로 표현하지 않았다. 장시간 실행과 재시작에서도 같은 규칙이 유지되는지 확인하기 위해 임시 "
        "자원과 영구 상태를 구분했으며, 실험 종료 뒤 제거할 수 있는 파일과 계속 보존할 증거도 별도로 정했다."
    )
    sixth = (
        f"마지막 검토에서는 {verification}. 검토자는 작성 순서를 따라가는 대신 요구사항에서 실제 산출물로 "
        "역추적해 누락된 조건이 없는지 살폈고, 이름이 비슷한 fixture가 정답으로 오인되지 않는지도 확인했다. "
        "자동 검사 결과는 원문 일부와 직접 대조했으며 hash, 페이지, chunk, owner, version 같은 식별 가능한 "
        "관찰값을 기록했다. 이 기록은 실험이 성공했다는 인상을 만들기 위한 설명이 아니라 다음 실행에서 같은 "
        "조건을 재현하고 차이를 판단하기 위한 기준선이며, 확인하지 않은 production 효과를 포함하지 않는다."
    )
    return [first, second, third, fourth, fifth, sixth]


def build_pdf(source_path: Path, output_path: Path) -> None:
    source = json.loads(source_path.read_text(encoding="utf-8"))
    regular, bold = register_fonts()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        leftMargin=19 * mm,
        rightMargin=19 * mm,
        topMargin=23 * mm,
        bottomMargin=20 * mm,
        title=source["title"],
        author="PRIZM synthetic evaluation fixture",
        subject=source["classification"],
        pageCompression=1,
    )
    base = getSampleStyleSheet()
    styles = {
        "cover": ParagraphStyle(
            "Cover",
            parent=base["Title"],
            fontName=bold,
            fontSize=24,
            leading=34,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#18233B"),
            spaceAfter=14 * mm,
        ),
        "subtitle": ParagraphStyle(
            "Subtitle",
            parent=base["Normal"],
            fontName=regular,
            fontSize=11,
            leading=19,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#49566E"),
            spaceAfter=10 * mm,
        ),
        "notice": ParagraphStyle(
            "Notice",
            parent=base["Normal"],
            fontName=bold,
            fontSize=10,
            leading=18,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#8A2A2A"),
            borderColor=colors.HexColor("#D8A7A7"),
            borderWidth=0.8,
            borderPadding=10,
            backColor=colors.HexColor("#FFF7F7"),
        ),
        "heading": ParagraphStyle(
            "Heading",
            parent=base["Heading1"],
            fontName=bold,
            fontSize=14,
            leading=21,
            textColor=colors.HexColor("#193B68"),
            spaceBefore=7 * mm,
            spaceAfter=4 * mm,
            keepWithNext=True,
        ),
        "body": ParagraphStyle(
            "Body",
            parent=base["BodyText"],
            fontName=regular,
            fontSize=9.2,
            leading=15.2,
            alignment=TA_JUSTIFY,
            textColor=colors.HexColor("#1E2530"),
            firstLineIndent=5 * mm,
            wordWrap="CJK",
            spaceAfter=4.2 * mm,
        ),
        "toc": ParagraphStyle(
            "Toc",
            parent=base["BodyText"],
            fontName=regular,
            fontSize=9.4,
            leading=17,
            alignment=TA_LEFT,
            textColor=colors.HexColor("#2F3A4A"),
        ),
    }

    story = [
        Spacer(1, 46 * mm),
        Paragraph(source["title"], styles["cover"]),
        Paragraph(source["subtitle"], styles["subtitle"]),
        Spacer(1, 12 * mm),
        Paragraph(source["classification"], styles["notice"]),
        Spacer(1, 26 * mm),
        Paragraph(
            "이 문서는 검색 평가를 위해 생성한 장문 합성 자료입니다. 모든 사건, 역할, 수치와 "
            "식별자는 실제 경력을 서술하지 않으며 production 데이터로 사용하지 않습니다.",
            styles["subtitle"],
        ),
        PageBreak(),
        Paragraph("문서 구성", styles["heading"]),
    ]
    for section in source["sections"]:
        story.append(Paragraph(section["title"], styles["toc"]))
    story.extend([PageBreak()])

    for section in source["sections"]:
        story.append(Paragraph(section["title"], styles["heading"]))
        for body in paragraphs(section):
            story.append(Paragraph(body, styles["body"]))

    def decorate_page(pdf_canvas: canvas.Canvas, current_doc: SimpleDocTemplate) -> None:
        page = pdf_canvas.getPageNumber()
        width, height = A4
        pdf_canvas.saveState()
        pdf_canvas.setStrokeColor(colors.HexColor("#D8DEE8"))
        pdf_canvas.setLineWidth(0.5)
        pdf_canvas.line(19 * mm, height - 15 * mm, width - 19 * mm, height - 15 * mm)
        pdf_canvas.setFont(regular, 7.5)
        pdf_canvas.setFillColor(colors.HexColor("#667085"))
        pdf_canvas.drawString(19 * mm, height - 11.5 * mm, "PRIZM P16 · SYNTHETIC EVALUATION DOCUMENT")
        pdf_canvas.drawRightString(width - 19 * mm, 11.5 * mm, f"{page} 페이지")
        pdf_canvas.drawString(19 * mm, 11.5 * mm, "실제 인물·회사·경력 정보 없음")
        pdf_canvas.restoreState()

    doc.build(
        story,
        onFirstPage=decorate_page,
        onLaterPages=decorate_page,
        canvasmaker=DeterministicCanvas,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding="utf-8"))
    output = args.output or Path(source["outputPath"])
    build_pdf(args.source, output)
    print(output.resolve())


if __name__ == "__main__":
    main()
