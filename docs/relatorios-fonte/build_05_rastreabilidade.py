# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Matriz de Rastreabilidade",
              "Requisito funcional, caso de uso, componente de código e caso de teste")

story.append(Paragraph("1 OBJETIVO", styles["H1"]))
story.append(Paragraph(
    "Esta matriz relaciona cada requisito funcional ao caso de uso, ao componente de código e "
    "ao caso de teste correspondentes, evidenciando a cobertura da implementação e servindo de "
    "roteiro de consulta durante a arguição da banca. Requisitos classificados como Visão "
    "Futura não possuem componente nem caso de teste, por estarem fora do escopo atual.",
    styles["Body"]))

rows = [
    ("RF01", "Autenticar-se", "4.3", "Usuario, AuthService, JwtService, SecurityConfig", "CT13–CT31"),
    ("RF02", "Cadastrar imóvel", "4.3", "Imovel, Endereco, ImovelRepository", "CT10"),
    # RF04 passa a ter endpoint proprio; antes so havia o repositorio.
    ("RF03", "Editar ou inativar imóvel", "4.3", "Imovel, ImovelRepository", "—"),
    ("RF04", "Listar imóveis por situação", "4.3", "ImovelController, ImovelRepository", "—"),
    ("RF05", "Cadastrar reserva", "4.1", "Reserva, ReservaRepository", "CT11"),
    ("RF06", "Verificar conflito de datas", "4.1", "ReservaRepository", "CT01–CT06"),
    ("RF07", "Cancelar ou concluir reserva", "4.3", "Reserva, ReservaRepository", "—"),
    ("RF08", "Gerar sugestão de preço via IA", "4.2", "SugestaoPreco, SugestaoPrecoService", "CT07"),
    ("RF09", "Aplicar cálculo de contingência", "4.2", "SugestaoPreco, SugestaoPrecoService", "CT08, CT09"),
    # RF06 permanece com CT01-CT06: apenas CT02 esta implementado ate aqui.
    ("RF10", "Exibir painel executivo", "4.3", "SugestaoPrecoRepository", "—"),
    ("RF11", "Consultar histórico de sugestões", "4.3", "SugestaoPrecoRepository", "—"),
    ("RF12", "Exibir reservas conflitantes no erro", "4.1", "ReservaRepository", "CT12"),
    ("RF13", "Sincronização iCal (Visão Futura)", "—", "—", "—"),
    ("RF14", "Gateway de pagamento real (Visão Futura)", "—", "—", "—"),
]
tdata = [[Paragraph("RF", styles["CellHeader"]), Paragraph("Descrição", styles["CellHeader"]),
          Paragraph("Caso de uso", styles["CellHeaderCenter"]), Paragraph("Componente", styles["CellHeader"]),
          Paragraph("Caso(s) de teste", styles["CellHeaderCenter"])]]
for rf, desc, uc, comp, ct in rows:
    tdata.append([Paragraph(rf, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(uc, styles["CellCenter"]), Paragraph(comp, styles["Cell"]),
                  Paragraph(ct, styles["CellCenter"])])

story.append(Paragraph("2 MATRIZ DE RASTREABILIDADE", styles["H1"]))
story.extend(quadro(styles, "Requisito funcional, caso de uso, componente e caso de teste", quadro_table(
    tdata, [12 * mm, 46 * mm, 18 * mm, 56 * mm, 22 * mm], font_size=8.4,
    header_align_center=[2, 4])))

story.append(Paragraph(
    "A coluna de caso de uso remete às seções do Relatório Técnico (Documento 1) nas quais "
    "cada caso é descrito; a coluna de casos de teste remete aos identificadores do Plano de "
    "Testes (Documento 4). Requisitos sem teste associado ainda não possuem caso definido e "
    "devem ser complementados à medida que os componentes correspondentes forem "
    "implementados.", styles["Body"]))

story.append(Paragraph(
    "A referência de um requisito a um intervalo de casos não implica que todos estejam "
    "executados: o Plano de Testes indica, caso a caso, quais já possuem implementação. O "
    "RF01 é hoje o requisito de maior cobertura, com dezenove casos automatizados; o RF06 "
    "referencia seis casos, dos quais apenas o CT02 está implementado.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "05_MATRIZ_DE_RASTREABILIDADE.pdf"), "Matriz de Rastreabilidade")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 05_MATRIZ_DE_RASTREABILIDADE.pdf")
