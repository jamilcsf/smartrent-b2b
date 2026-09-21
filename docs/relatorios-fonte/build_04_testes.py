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
    ("Testes de repositório", "Anotação @DataJpaTest, validando as consultas de "
                               "ReservaRepository responsáveis pela verificação de conflito. "
                               "Ainda não implementados."),
    ("Testes de serviço simulados", "Simulação do cliente HTTP da Groq API (sucesso, tempo "
                                     "excedido e erro), garantindo que o cálculo de contingência "
                                     "é acionado corretamente."),
    ("Testes de contrato de API", "Anotação @WebMvcTest com MockMvc, exercitando a cadeia "
                                   "de filtros de segurança real. É o nível que comprova o "
                                   "acesso público por padrão e a resposta 401 nas rotas que "
                                   "exigem identificação."),
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
    ("CT01", "Criar reserva sem sobreposição de datas para o imóvel", "RF06", "Unitário/Repositório", "Crítica", "Planejado"),
    ("CT02", "Rejeitar reserva com sobreposição total de datas (mesmo período exato)", "RF06", "Unitário/Repositório", "Crítica", "Implementado"),
    ("CT03", "Rejeitar reserva com sobreposição parcial no início ou no fim do período", "RF06", "Unitário/Repositório", "Crítica", "Planejado"),
    ("CT04", "Permitir reserva em datas adjacentes, sem sobreposição real", "RF06", "Unitário/Repositório", "Alta", "Planejado"),
    ("CT05", "Ignorar reservas com situação cancelada na checagem de conflito", "RF06", "Unitário/Repositório", "Crítica", "Planejado"),
    ("CT06", "Excluir a própria reserva da checagem ao editar seus dados", "RF06", "Unitário/Repositório", "Alta", "Planejado"),
    ("CT07", "Registrar sugestão de preço com origem IA generativa em chamada bem-sucedida", "RF08", "Serviço (simulado)", "Crítica", "Planejado"),
    ("CT08", "Acionar o cálculo de contingência quando a chamada à IA excede o tempo limite", "RF09", "Serviço (simulado)", "Crítica", "Planejado"),
    ("CT09", "Acionar o cálculo de contingência quando a IA retorna erro", "RF09", "Serviço (simulado)", "Crítica", "Implementado"),
    ("CT10", "Rejeitar cadastro de imóvel sem latitude ou longitude", "RF02", "Unitário", "Média", "Planejado"),
    ("CT11", "Rejeitar reserva com data de saída anterior ou igual à de entrada", "RF05", "Unitário", "Alta", "Planejado"),
    ("CT12", "Retornar as reservas conflitantes no contrato REST de erro", "RF12", "Integração", "Alta", "Planejado"),
    ("CT13", "Recusar autenticação com credenciais inválidas", "RF01", "Contrato de API", "Média", "Implementado"),
    ("CT14", "Emitir token e recuperar dele o usuário de origem", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT15", "Recusar token adulterado", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT16", "Recusar token assinado com outro segredo", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT17", "Recusar token expirado", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT18", "Tratar token malformado sem interromper a requisição", "RF01", "Unitário", "Alta", "Implementado"),
    ("CT19", "Gravar a senha como hash, nunca em texto puro", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT20", "Normalizar o endereço de e-mail no cadastro", "RF01", "Unitário", "Média", "Implementado"),
    ("CT21", "Recusar cadastro com confirmação de senha divergente", "RF01", "Unitário", "Alta", "Implementado"),
    ("CT22", "Recusar cadastro com e-mail já existente", "RF01", "Unitário", "Alta", "Implementado"),
    ("CT23", "Emitir token no login quando a senha confere", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT24", "Recusar login com senha errada", "RF01", "Unitário", "Crítica", "Implementado"),
    ("CT25", "Responder a mesma mensagem para e-mail inexistente e senha errada", "RF01", "Unitário", "Alta", "Implementado"),
    ("CT26", "Recusar login de usuário inativo", "RF01", "Unitário", "Alta", "Implementado"),
    ("CT27", "Responder 401 em JSON na rota que exige identificação", "RF01", "Contrato de API", "Crítica", "Implementado"),
    ("CT28", "Manter rota pública acessível sem token (acesso público por padrão)", "RF01", "Contrato de API", "Crítica", "Implementado"),
    ("CT29", "Permitir login a visitante e devolver o token", "RF01", "Contrato de API", "Alta", "Implementado"),
    ("CT30", "Detalhar o erro de cada campo no cadastro inválido", "RF01", "Contrato de API", "Média", "Implementado"),
    ("CT31", "Responder 409 para e-mail já cadastrado", "RF01", "Contrato de API", "Média", "Implementado"),
]
cdata = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Caso de teste", styles["CellHeader"]),
          Paragraph("RF", styles["CellHeaderCenter"]), Paragraph("Nível", styles["CellHeader"]),
          Paragraph("Prior.", styles["CellHeaderCenter"]), Paragraph("Situação", styles["CellHeaderCenter"])]]
for cid, desc, rf, nivel, prio, situacao in casos:
    prio_style = styles["CellCenter"] if prio not in ("Crítica",) else \
        ParagraphStyle("pb", parent=styles["CellCenter"], fontName="Times-Bold")
    sit_style = ParagraphStyle("st", parent=styles["CellCenter"],
                               fontName="Times-Bold" if situacao == "Implementado" else "Times-Roman")
    cdata.append([Paragraph(cid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(rf, styles["CellCenter"]), Paragraph(nivel, styles["Cell"]),
                  Paragraph(prio, prio_style), Paragraph(situacao, sit_style)])
story.extend(quadro(styles, "Matriz de casos de teste", quadro_table(
    cdata, [12 * mm, 52 * mm, 11 * mm, 27 * mm, 16 * mm, 24 * mm], font_size=8.0,
    header_align_center=[2, 4, 5])))

story.append(Paragraph(
    "A suíte automatizada conta atualmente com 21 casos executados a cada integração, "
    "correspondentes aos 21 identificadores marcados como implementados no quadro acima. "
    "Nenhum deles depende de banco de dados ou de rede, de modo que a esteira de integração "
    "contínua executa a suíte completa sem infraestrutura auxiliar, conforme o RNF04.",
    styles["Body"]))

story.append(Paragraph(
    "A cobertura concentra-se hoje na autenticação (RF01) e nos dois casos de regra de negócio "
    "já implementados, CT02 e CT09. Os casos ainda planejados dependem de componentes "
    "inexistentes: a verificação de conflito em nível de repositório exige os testes com "
    "@DataJpaTest, e os casos CT07 e CT08 pressupõem a integração real com a IA generativa, "
    "hoje substituída por um valor fixo no código de produção.", styles["Body"]))

story.append(Paragraph(
    "A validade da suíte foi aferida por teste de mutação: alterando deliberadamente a "
    "configuração de segurança para exigir autenticação em todas as rotas, cinco dos seis "
    "casos de contrato de API passaram a falhar, entre eles o CT28. O exercício confirma que "
    "os casos detectam a regressão que se propõem a detectar, e não apenas acompanham o "
    "comportamento corrente.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "04_PLANO_DE_TESTES.pdf"), "Plano de Testes")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 04_PLANO_DE_TESTES.pdf")
