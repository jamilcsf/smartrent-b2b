# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Plano de Testes",
              "Estratégia de testes automatizados e matriz de casos de teste do MVP")

story.append(Paragraph("1 ESTRATÉGIA DE TESTES", styles["H1"]))
story.append(Paragraph(
    "Conforme o protocolo do projeto, os testes automatizados usam JUnit 5 e Mockito. As "
    "chamadas à IA generativa e a qualquer serviço externo são sempre simuladas nos testes de "
    "build, de modo que nenhum teste automatizado dependa de rede ou de credenciais reais.",
    styles["Body"]))
niveis = [
    ("Testes unitários", "Regras de negócio isoladas, em especial o cálculo de sobreposição de "
                          "datas (RF06) e a lógica do cálculo de contingência (RF09), sem "
                          "contexto do Spring."),
    ("Testes de repositório", "Anotação @DataJpaTest com banco em memória, validando as "
                               "consultas de ReservaRepository responsáveis pela verificação de "
                               "conflito."),
    ("Testes de serviço simulados", "Simulação do cliente HTTP da Groq API (sucesso, tempo "
                                     "excedido e erro), garantindo que o cálculo de contingência "
                                     "é acionado corretamente."),
    ("Testes de integração de API", "Anotação @SpringBootTest com MockMvc, validando os "
                                     "contratos REST dos módulos de imóveis, reservas e "
                                     "sugestão de preço."),
]
ndata = [[Paragraph("Nível", styles["CellHeader"]), Paragraph("Escopo", styles["CellHeader"])]]
for n, e in niveis:
    ndata.append([Paragraph(n, styles["CellBold"]), Paragraph(e, styles["Cell"])])
story.extend(quadro(styles, "Níveis de teste adotados", quadro_table(ndata, [44 * mm, 108 * mm])))

story.append(Paragraph("2 MATRIZ DE CASOS DE TESTE", styles["H1"]))
story.append(Paragraph(
    "A prioridade crítica marca os casos que cobrem requisitos obrigatórios de maior risco, a "
    "verificação de conflito de datas e o cálculo de contingência, que devem ser os primeiros "
    "a ser implementados assim que a camada de serviço existir.", styles["Body"]))

casos = [
    ("CT01", "Criar reserva sem sobreposição de datas para o imóvel", "RF06", "Unitário/Repositório", "Crítica"),
    ("CT02", "Rejeitar reserva com sobreposição total de datas (mesmo período exato)", "RF06", "Unitário/Repositório", "Crítica"),
    ("CT03", "Rejeitar reserva com sobreposição parcial no início ou no fim do período", "RF06", "Unitário/Repositório", "Crítica"),
    ("CT04", "Permitir reserva em datas adjacentes, sem sobreposição real", "RF06", "Unitário/Repositório", "Alta"),
    ("CT05", "Ignorar reservas com situação cancelada na checagem de conflito", "RF06", "Unitário/Repositório", "Crítica"),
    ("CT06", "Excluir a própria reserva da checagem ao editar seus dados", "RF06", "Unitário/Repositório", "Alta"),
    ("CT07", "Registrar sugestão de preço com origem IA generativa em chamada bem-sucedida", "RF08", "Serviço (simulado)", "Crítica"),
    ("CT08", "Acionar o cálculo de contingência quando a chamada à IA excede o tempo limite", "RF09", "Serviço (simulado)", "Crítica"),
    ("CT09", "Acionar o cálculo de contingência quando a IA retorna erro", "RF09", "Serviço (simulado)", "Crítica"),
    ("CT10", "Rejeitar cadastro de imóvel sem latitude ou longitude", "RF02", "Unitário", "Média"),
    ("CT11", "Rejeitar reserva com data de saída anterior ou igual à de entrada", "RF05", "Unitário", "Alta"),
    ("CT12", "Retornar as reservas conflitantes no contrato REST de erro", "RF12", "Integração", "Alta"),
    ("CT13", "Recusar autenticação com credenciais inválidas", "RF01", "Integração", "Média"),
]
cdata = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Caso de teste", styles["CellHeader"]),
          Paragraph("RF", styles["CellHeaderCenter"]), Paragraph("Nível", styles["CellHeader"]),
          Paragraph("Prioridade", styles["CellHeaderCenter"])]]
for cid, desc, rf, nivel, prio in casos:
    prio_style = styles["CellCenter"] if prio not in ("Crítica",) else \
        ParagraphStyle("pb", parent=styles["CellCenter"], fontName="Times-Bold")
    cdata.append([Paragraph(cid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(rf, styles["CellCenter"]), Paragraph(nivel, styles["Cell"]),
                  Paragraph(prio, prio_style)])
story.extend(quadro(styles, "Matriz de casos de teste planejados", quadro_table(
    cdata, [14 * mm, 63 * mm, 12 * mm, 33 * mm, 20 * mm], font_size=8.4,
    header_align_center=[2, 4])))

story.append(Paragraph(
    "Até o fechamento desta versão do documento, a camada de serviço ainda não havia sido "
    "implementada; por isso nenhum dos casos acima possui execução registrada. A coluna de "
    "situação será incorporada a esta matriz assim que a primeira suíte de testes for "
    "executada, e cruzada com a matriz de rastreabilidade de requisitos.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "04_PLANO_DE_TESTES.pdf"), "Plano de Testes")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 04_PLANO_DE_TESTES.pdf")
