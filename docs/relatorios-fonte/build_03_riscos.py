# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Matriz de Riscos",
              "Identificação, probabilidade, impacto e mitigação dos principais riscos do MVP")

story.append(Paragraph("1 METODOLOGIA", styles["H1"]))
story.append(Paragraph(
    "Cada risco é classificado por probabilidade (baixa, média ou alta) e por impacto (baixo, "
    "médio ou alto) sobre o prazo, a qualidade ou o escopo do MVP de 30 dias. A severidade "
    "combina as duas dimensões e ordena a atenção da equipe: quanto maior a ênfase tipográfica "
    "no quadro a seguir, maior a severidade.", styles["Body"]))

riscos = [
    ("R01", "Indisponibilidade ou instabilidade da Groq API durante o desenvolvimento ou em "
            "produção.", "Média", "Médio", "Média",
     "O cálculo de contingência já é requisito obrigatório (RF09), com a origem de cada "
     "sugestão registrada; o sistema nunca fica sem um valor sugerido."),
    ("R02", "Falha na verificação de conflito de datas permitir reservas sobrepostas, o risco "
            "mais crítico do domínio de negócio.", "Baixa", "Alto", "Média",
     "Verificação na camada de aplicação, reforçada por controle de concorrência otimista; "
     "cobertura de testes dedicada a todos os casos de sobreposição."),
    ("R03", "Instabilidade ou limite do plano gratuito do Supabase afetar a disponibilidade "
            "durante a apresentação à banca.", "Média", "Médio", "Média",
     "Ambiente validado com antecedência; manutenção de uma cópia local do esquema e dos "
     "dados de demonstração."),
    ("R04", "Aumento do escopo pressionar o cronograma de 30 dias.", "Alta", "Médio", "Alta",
     "O protocolo do projeto já classifica qualquer pedido fora da matriz MoSCoW acordada "
     "como Visão Futura, preservando o cronograma."),
    ("R05", "Bloqueios inesperados no ambiente de desenvolvimento local, como já ocorreu com a "
            "ausência do Maven e do wrapper, e com a reescrita indevida de pacotes por uma "
            "extensão de IDE.", "Alta", "Baixo", "Média",
     "Uso do Maven Wrapper versionado no repositório; documentação viva (STATUS_PROJETO.md e "
     "ADRs) registra cada incidente e sua resolução."),
    ("R06", "Falta de rastreabilidade entre requisitos, código e testes dificultar a arguição "
            "da banca.", "Média", "Médio", "Média",
     "Matriz de rastreabilidade de requisitos mantida atualizada a cada entrega."),
    ("R07", "Cobertura insuficiente de testes automatizados na reta final do prazo.", "Média",
     "Alto", "Alta", "O plano de testes prioriza desde já os casos críticos, conflito de "
     "datas e contingência de IA, antes dos casos de borda secundários."),
    ("R08", "Falhas expostas durante a demonstração ao vivo para a banca.", "Média", "Alto",
     "Alta", "Ensaio da apresentação com o ambiente já implantado, com antecedência mínima de "
     "dois dias, e gravação prévia de um vídeo de segurança da demonstração."),
]
rdata = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Risco", styles["CellHeader"]),
          Paragraph("Prob.", styles["CellHeaderCenter"]), Paragraph("Impacto", styles["CellHeaderCenter"]),
          Paragraph("Sever.", styles["CellHeaderCenter"]), Paragraph("Mitigação", styles["CellHeader"])]]
peso = {"Baixa": 0, "Baixo": 0, "Média": 1, "Médio": 1, "Alta": 2, "Alto": 2}
for rid, desc, prob, imp, sev, mit in riscos:
    sev_style = styles["CellCenter"] if peso.get(sev, 0) < 2 else styles["CellBold"]
    sev_style = ParagraphStyle("sevc", parent=sev_style, alignment=TA_CENTER)
    rdata.append([Paragraph(rid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(prob, styles["CellCenter"]), Paragraph(imp, styles["CellCenter"]),
                  Paragraph(sev, sev_style), Paragraph(mit, styles["Cell"])])

story.append(Paragraph("2 REGISTRO DE RISCOS", styles["H1"]))
story.extend(quadro(styles, "Registro de riscos do projeto", quadro_table(
    rdata, [10 * mm, 42 * mm, 13 * mm, 18 * mm, 13 * mm, 50 * mm], font_size=8.4,
    header_align_center=[2, 3, 4])))

story.append(Paragraph(
    "Os riscos R04, R07 e R08 concentram a maior severidade combinada. Recomenda-se revisão "
    "semanal deste documento pela equipe, com o registro explícito de qualquer risco novo "
    "identificado ao longo do desenvolvimento.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "03_MATRIZ_DE_RISCOS.pdf"), "Matriz de Riscos")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 03_MATRIZ_DE_RISCOS.pdf")
