# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

A = os.path.join(BASE, "assets")
styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Cronograma",
              "Planejamento macro do MVP em 30 dias corridos, alinhado à matriz MoSCoW")

story.append(Paragraph("1 VISÃO GERAL", styles["H1"]))
story.append(Paragraph(
    "O escopo do SmartRent B2B foi delimitado para caber em 30 dias corridos. Qualquer demanda "
    "fora desse prazo, como aplicativos móveis nativos, um gateway de pagamento real ou a "
    "sincronização bidirecional de reservas via iCal com plataformas externas, é registrada "
    "como Visão Futura e não integra este cronograma.", styles["Body"]))
story.append(figura(styles, f"{A}/gantt_cronograma.png", "cronograma macro do MVP", 158, 610 / 1568))

story.append(Paragraph("2 DETALHAMENTO POR FASE", styles["H1"]))
fases = [
    ("Semana 1 — Fundação", "Dias 1 a 7",
     "Inicialização do projeto Maven e Spring Boot, modelagem das entidades JPA (Usuario, "
     "Imovel, Reserva, SugestaoPreco), repositórios com a verificação de conflito de datas, "
     "geração e validação do esquema por migration contra um PostgreSQL real.", "Em andamento"),
    ("Semana 2 — Domínio de negócio", "Dias 8 a 14",
     "Objetos de transferência de dados (Records), camada de serviço com a checagem de "
     "conflito dentro de transações, controladores REST e tratamento centralizado de "
     "exceções.", "Planejada"),
    ("Semanas 2-3 — Motor de IA", "Dias 12 a 17",
     "Integração com a Groq API (Llama 3.3) por meio do cliente HTTP nativo do Spring, "
     "implementação e testes do cálculo de contingência, registro auditável da origem de cada "
     "sugestão de preço.", "Planejada"),
    ("Semana 3 — Interface", "Dias 15 a 22",
     "Telas de gestão de imóveis, gestão de reservas, precificação inteligente e painel "
     "executivo, em HTML5, Tailwind CSS e Chart.js.", "Planejada"),
    ("Semana 4 — Qualidade", "Dias 23 a 27",
     "Testes unitários com JUnit 5 e Mockito, esteira de integração contínua e implantação em "
     "Render (backend) e Vercel (frontend).", "Planejada"),
    ("Semana 4 — Entrega", "Dias 28 a 30",
     "Consolidação do relatório final, revisão da documentação de gestão e ensaio da "
     "apresentação à banca.", "Planejada"),
]
fdata = [[Paragraph("Fase", styles["CellHeader"]), Paragraph("Período", styles["CellHeaderCenter"]),
          Paragraph("Entregas", styles["CellHeader"]), Paragraph("Situação", styles["CellHeaderCenter"])]]
for nome, periodo, entregas, status in fases:
    st_style = styles["CellCenter"] if status == "Planejada" else styles["CellBold"]
    fdata.append([Paragraph(nome, styles["CellBold"]), Paragraph(periodo, styles["CellCenter"]),
                  Paragraph(entregas, styles["Cell"]),
                  Paragraph(status, ParagraphStyle("st", parent=st_style, alignment=TA_CENTER))])
story.extend(quadro(styles, "Detalhamento das fases do cronograma", quadro_table(
    fdata, [32 * mm, 20 * mm, 84 * mm, 20 * mm], header_align_center=[1, 3])))

story.append(Paragraph(
    "A partir de 21 de setembro o projeto passou a ter como base a implementação "
    "desenvolvida por Jamil Cherem, o que antecipou entregas previstas para as Semanas 2, "
    "3 e 4 — camada de serviço, controladores REST, telas com Chart.js, testes unitários e "
    "esteira de integração contínua já existem. Em contrapartida, a Semana 1 permanece em "
    "andamento: a persistência já migrou para PostgreSQL com esquema versionado por "
    "migration e validado contra banco real, mas a modelagem ainda não contempla as "
    "entidades de usuário e imóvel. As decisões técnicas que orientam essa convergência "
    "estão registradas na ADR-002.",
    styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "02_CRONOGRAMA.pdf"), "Cronograma do Projeto")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 02_CRONOGRAMA.pdf")
