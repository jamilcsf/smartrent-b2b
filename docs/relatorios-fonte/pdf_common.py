# -*- coding: utf-8 -*-
"""Camada comum ABNT para os documentos do SmartRent B2B (Projeto Aplicado IV)."""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm, cm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY, TA_RIGHT
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
                                 Image, PageBreak, HRFlowable, KeepTogether, ListFlowable,
                                 ListItem)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.pdfgen import canvas as pdfcanvas

BLACK = colors.black
GRAY_HEADER = colors.HexColor("#E5E7EB")
GRAY_ZEBRA = colors.HexColor("#F8FAFC")
GRAY_RULE = colors.HexColor("#9CA3AF")

PAGE_W, PAGE_H = A4
M_LEFT = 3 * cm
M_TOP = 3 * cm
M_RIGHT = 2 * cm
M_BOTTOM = 2 * cm

AUTORES = ["Arthur Moreira", "Douglas do Carmo", "Jamil Cherem"]
ORIENTADORA = "Profa. Milena Maredmi Correa"
ANO = "2026"
CIDADE = "Florianópolis"


def get_styles():
    s = {}
    s["Body"] = ParagraphStyle("Body", fontName="Times-Roman", fontSize=12, leading=18,
                                alignment=TA_JUSTIFY, firstLineIndent=1.25 * cm, spaceAfter=0,
                                textColor=BLACK)
    s["BodyNoIndent"] = ParagraphStyle("BodyNoIndent", parent=s["Body"], firstLineIndent=0)
    s["H1"] = ParagraphStyle("H1", fontName="Times-Bold", fontSize=12, leading=18,
                              spaceBefore=24, spaceAfter=12, textColor=BLACK, alignment=TA_LEFT,
                              keepWithNext=True)
    s["H2"] = ParagraphStyle("H2", fontName="Times-Bold", fontSize=12, leading=18,
                              spaceBefore=18, spaceAfter=8, textColor=BLACK, alignment=TA_LEFT,
                              keepWithNext=True)
    s["H3"] = ParagraphStyle("H3", fontName="Times-BoldItalic", fontSize=12, leading=18,
                              spaceBefore=12, spaceAfter=6, textColor=BLACK, alignment=TA_LEFT,
                              keepWithNext=True)
    s["CaptionTitle"] = ParagraphStyle("CaptionTitle", fontName="Times-Roman", fontSize=10,
                                        leading=12, alignment=TA_CENTER, spaceBefore=10,
                                        spaceAfter=4, textColor=BLACK)
    s["CaptionSource"] = ParagraphStyle("CaptionSource", fontName="Times-Roman", fontSize=10,
                                         leading=12, alignment=TA_CENTER, spaceBefore=4,
                                         spaceAfter=14, textColor=BLACK)
    s["Cell"] = ParagraphStyle("Cell", fontName="Times-Roman", fontSize=9.3, leading=11.6,
                                alignment=TA_LEFT, textColor=BLACK)
    s["CellBold"] = ParagraphStyle("CellBold", parent=s["Cell"], fontName="Times-Bold")
    s["CellHeader"] = ParagraphStyle("CellHeader", fontName="Times-Bold", fontSize=9.3,
                                      leading=11.6, alignment=TA_LEFT, textColor=BLACK)
    s["CellCenter"] = ParagraphStyle("CellCenter", parent=s["Cell"], alignment=TA_CENTER)
    s["CellHeaderCenter"] = ParagraphStyle("CellHeaderCenter", parent=s["CellHeader"], alignment=TA_CENTER)
    s["CoverInst"] = ParagraphStyle("CoverInst", fontName="Times-Roman", fontSize=12, leading=16,
                                     alignment=TA_CENTER, textColor=BLACK)
    s["CoverAuthor"] = ParagraphStyle("CoverAuthor", fontName="Times-Roman", fontSize=12,
                                       leading=16, alignment=TA_CENTER, textColor=BLACK)
    s["CoverTitle"] = ParagraphStyle("CoverTitle", fontName="Times-Bold", fontSize=14, leading=19,
                                      alignment=TA_CENTER, textColor=BLACK)
    s["CoverSubtitle"] = ParagraphStyle("CoverSubtitle", fontName="Times-Roman", fontSize=12,
                                         leading=16, alignment=TA_CENTER, textColor=BLACK)
    s["RostoNatureza"] = ParagraphStyle("RostoNatureza", fontName="Times-Roman", fontSize=11,
                                         leading=15, alignment=TA_JUSTIFY, textColor=BLACK,
                                         leftIndent=7.5 * cm)
    s["ResumoTitle"] = ParagraphStyle("ResumoTitle", fontName="Times-Bold", fontSize=12,
                                       leading=18, alignment=TA_CENTER, spaceAfter=14)
    s["Resumo"] = ParagraphStyle("Resumo", fontName="Times-Roman", fontSize=12, leading=18,
                                  alignment=TA_JUSTIFY, textColor=BLACK, firstLineIndent=0)
    s["Referencia"] = ParagraphStyle("Referencia", fontName="Times-Roman", fontSize=12,
                                      leading=14, alignment=TA_LEFT, spaceAfter=14,
                                      textColor=BLACK, firstLineIndent=0)
    return s


def quadro_table(data, col_widths, font_size=9.3, header_align_center=None, zebra=True):
    header_align_center = header_align_center or []
    t = Table(data, colWidths=col_widths, repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), GRAY_HEADER),
        ("TEXTCOLOR", (0, 0), (-1, 0), BLACK),
        ("FONTNAME", (0, 0), (-1, 0), "Times-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), font_size),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.9, BLACK),
        ("LINEABOVE", (0, 0), (-1, 0), 0.9, BLACK),
        ("LINEBELOW", (0, -1), (-1, -1), 0.9, BLACK),
        ("INNERGRID", (0, 0), (-1, -1), 0.4, GRAY_RULE),
        ("BOX", (0, 0), (-1, -1), 0.6, GRAY_RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    if zebra:
        for i in range(1, len(data)):
            if i % 2 == 0:
                style.append(("BACKGROUND", (0, i), (-1, i), GRAY_ZEBRA))
    for c in header_align_center:
        style.append(("ALIGN", (c, 0), (c, -1), "CENTER"))
    t.setStyle(TableStyle(style))
    return t


_counters = {"quadro": 0, "figura": 0}


def reset_counters():
    _counters["quadro"] = 0
    _counters["figura"] = 0


def quadro(styles, titulo, table_flowable, fonte="elaborado pelos autores (2026)"):
    """Título e fonte ficam presos às bordas da tabela (keepWithNext), mas a
    tabela em si pode quebrar entre páginas quando for longa demais — o
    cabeçalho se repete (repeatRows=1) em cada continuação."""
    _counters["quadro"] += 1
    n = _counters["quadro"]
    return [
        Paragraph(f"Quadro {n} – {titulo}", styles["CaptionTitle"]),
        table_flowable,
        Paragraph(f"Fonte: {fonte}.", styles["CaptionSource"]),
    ]


def figura(styles, path, titulo, width_mm_, aspect, fonte="elaborado pelos autores (2026)"):
    _counters["figura"] += 1
    n = _counters["figura"]
    w = width_mm_ * mm
    h = w * aspect
    return KeepTogether([
        Paragraph(f"Figura {n} – {titulo}", styles["CaptionTitle"]),
        Image(path, width=w, height=h),
        Paragraph(f"Fonte: {fonte}.", styles["CaptionSource"]),
    ])


def section_rule():
    return HRFlowable(width="100%", thickness=0.6, color=GRAY_RULE, spaceBefore=0, spaceAfter=4)


class ABNTDocTemplate(SimpleDocTemplate):
    """DocTemplate com numeração condicional de página (oculta nas folhas
    pré-textuais, conforme NBR 14724) e suporte a Sumário via multiBuild."""

    def __init__(self, *args, **kwargs):
        SimpleDocTemplate.__init__(self, *args, **kwargs)
        self._intro_page = None

    def afterFlowable(self, flowable):
        try:
            style_name = flowable.style.name
        except AttributeError:
            return
        if style_name == "H1":
            text = flowable.getPlainText()
            self.notify("TOCEntry", (0, text, self.page))
            if text.strip().upper().startswith("1 INTRODU"):
                self._intro_page = self.page
        elif style_name == "H2":
            self.notify("TOCEntry", (1, flowable.getPlainText(), self.page))


def make_header_footer_condicional():
    def _draw(c: pdfcanvas.Canvas, doc):
        c.saveState()
        intro_page = getattr(doc, "_intro_page", None)
        if intro_page is not None and doc.page >= intro_page:
            c.setFont("Times-Roman", 10)
            c.setFillColor(BLACK)
            c.drawRightString(PAGE_W - M_RIGHT, PAGE_H - M_TOP + 12, str(doc.page))
        c.restoreState()
    return _draw


def make_header_footer_simples(start_page=2):
    def _draw(c: pdfcanvas.Canvas, doc):
        c.saveState()
        if doc.page >= start_page:
            c.setFont("Times-Roman", 10)
            c.setFillColor(BLACK)
            c.drawRightString(PAGE_W - M_RIGHT, PAGE_H - M_TOP + 12, str(doc.page))
        c.restoreState()
    return _draw


def new_doc(path, doc_title, use_toc_template=False):
    cls = ABNTDocTemplate if use_toc_template else SimpleDocTemplate
    return cls(path, pagesize=A4,
               leftMargin=M_LEFT, rightMargin=M_RIGHT, topMargin=M_TOP, bottomMargin=M_BOTTOM,
               title=doc_title, author=", ".join(AUTORES))


def s_bold_center():
    return ParagraphStyle("BoldCenter", fontName="Times-Bold", fontSize=12, leading=16,
                           alignment=TA_CENTER, textColor=BLACK)


def capa(styles, titulo, subtitulo):
    story = []
    story.append(Spacer(1, 0.2 * cm))
    story.append(Paragraph("UNISENAI", s_bold_center()))
    story.append(Paragraph("Tecnologia em Análise e Desenvolvimento de Sistemas", styles["CoverInst"]))
    story.append(Spacer(1, 3.2 * cm))
    for a in AUTORES:
        story.append(Paragraph(a, styles["CoverAuthor"]))
    story.append(Spacer(1, 3.4 * cm))
    story.append(Paragraph(titulo.upper(), styles["CoverTitle"]))
    story.append(Spacer(1, 4))
    story.append(Paragraph(subtitulo, styles["CoverSubtitle"]))
    story.append(Spacer(1, 7.6 * cm))
    story.append(Paragraph(CIDADE, styles["CoverInst"]))
    story.append(Paragraph(ANO, styles["CoverInst"]))
    story.append(PageBreak())
    return story


def folha_de_rosto(styles, titulo, subtitulo, natureza_texto):
    story = []
    story.append(Spacer(1, 0.2 * cm))
    for a in AUTORES:
        story.append(Paragraph(a, styles["CoverAuthor"]))
    story.append(Spacer(1, 3.4 * cm))
    story.append(Paragraph(titulo.upper(), styles["CoverTitle"]))
    story.append(Spacer(1, 4))
    story.append(Paragraph(subtitulo, styles["CoverSubtitle"]))
    story.append(Spacer(1, 2.4 * cm))
    story.append(Paragraph(natureza_texto, styles["RostoNatureza"]))
    story.append(Spacer(1, 0.4 * cm))
    story.append(Paragraph(f"Orientadora: {ORIENTADORA}", styles["RostoNatureza"]))
    story.append(Spacer(1, 4.2 * cm))
    story.append(Paragraph(CIDADE, styles["CoverInst"]))
    story.append(Paragraph(ANO, styles["CoverInst"]))
    story.append(PageBreak())
    return story


def resumo_page(styles, texto, palavras_chave):
    story = []
    story.append(Paragraph("RESUMO", styles["ResumoTitle"]))
    story.append(Paragraph(texto, styles["Resumo"]))
    story.append(Spacer(1, 10))
    story.append(Paragraph(f"<b>Palavras-chave</b>: {palavras_chave}.", styles["Resumo"]))
    story.append(PageBreak())
    return story


def sumario_page(styles):
    story = []
    story.append(Paragraph("SUMÁRIO", styles["ResumoTitle"]))
    toc = TableOfContents()
    toc.levelStyles = [
        ParagraphStyle(fontName="Times-Bold", fontSize=12, name="TOC0", leading=18,
                        firstLineIndent=0, leftIndent=0),
        ParagraphStyle(fontName="Times-Roman", fontSize=12, name="TOC1", leading=18,
                        firstLineIndent=0, leftIndent=14),
    ]
    story.append(toc)
    story.append(PageBreak())
    return story


def referencias_page(styles, itens):
    story = []
    story.append(Paragraph("REFERÊNCIAS", styles["H1"]))
    story.append(section_rule())
    for it in itens:
        story.append(Paragraph(it, styles["Referencia"]))
    return story
