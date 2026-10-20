# -*- coding: utf-8 -*-
"""Documento 7 — Análise de Viabilidade e Impacto Inicial do MVP (AV Inteligência de Dados).

Os números econômicos vêm de scripts/analise_viabilidade.py (fonte única):
mudar uma hipótese lá e regenerar este PDF mantém texto, quadros e gráficos coerentes.
"""
import os
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)
sys.path.insert(0, os.path.join(BASE, "..", "..", "scripts"))

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

import analise_viabilidade as av
from reportlab.platypus import CondPageBreak
from pdf_common import *

A = os.path.join(BASE, "assets")
os.makedirs(A, exist_ok=True)

# --------------------------------------------------------------------------
# Modelo econômico parametrizado (reaproveita as constantes do script)
# --------------------------------------------------------------------------


def custo_ia(sugestoes=None):
    s = av.SUGESTOES_POR_IMOVEL_MES if sugestoes is None else sugestoes
    return s * av.TOKENS_POR_SUGESTAO / 1_000_000 * av.USD_POR_MILHAO_TOKENS * av.BRL_POR_USD


def margem(preco, sugestoes=None):
    return preco * (1 - av.TAXA_GATEWAY) - custo_ia(sugestoes)


def equilibrio(fixos, preco, sugestoes=None):
    from math import ceil
    return ceil(fixos / margem(preco, sugestoes))


FIXOS = av.INFRA_FIXA_MES + av.OUTROS_FIXOS_MES
PRECO = av.ASSINATURA_POR_IMOVEL
M_BASE = margem(PRECO)
N_BASE = equilibrio(FIXOS, PRECO)
VIDA = 1 / av.CHURN_MENSAL
LTV_CLIENTE = M_BASE * av.IMOVEIS_POR_CLIENTE * VIDA

CENARIOS = [
    # nome, preço, fixos, churn, sugestões/imóvel/mês
    ("Pessimista", 19.90, 200.0, 0.10, 300),
    ("Base", PRECO, FIXOS, av.CHURN_MENSAL, av.SUGESTOES_POR_IMOVEL_MES),
    ("Otimista", 39.90, 125.0, 0.03, av.SUGESTOES_POR_IMOVEL_MES),
]


def brl(v, casas=2):
    s = f"{v:,.{casas}f}"
    return "R$ " + s.replace(",", "X").replace(".", ",").replace("X", ".")


def pct(v, casas=0):
    return f"{v * 100:.{casas}f}".replace(".", ",") + "%"


# --------------------------------------------------------------------------
# Gráficos
# --------------------------------------------------------------------------
COR = {"azul": "#1F4E79", "verde": "#2E7D32", "laranja": "#C65D00", "cinza": "#6B7280"}
plt.rcParams.update({"font.family": "serif", "font.size": 10})


def g_equilibrio():
    xs = list(range(0, 13))
    fig, ax = plt.subplots(figsize=(7.2, 3.8), dpi=200)
    ax.plot(xs, [FIXOS] * len(xs), color=COR["cinza"], ls="--", label=f"Custos fixos ({brl(FIXOS, 0)}/mês)")
    ax.plot(xs, [x * M_BASE for x in xs], color=COR["azul"], lw=2, label="Margem de contribuição total")
    ax.scatter([N_BASE], [N_BASE * M_BASE], color=COR["laranja"], zorder=5)
    ax.annotate(f"Equilíbrio: {N_BASE} imóveis", (N_BASE, N_BASE * M_BASE), xytext=(N_BASE + 1, N_BASE * M_BASE - 60),
                arrowprops=dict(arrowstyle="->", color=COR["laranja"]), color=COR["laranja"])
    ax.set_xlabel("Imóveis pagantes")
    ax.set_ylabel("R$ por mês")
    ax.grid(alpha=.25)
    ax.legend(loc="upper left", frameon=False)
    fig.tight_layout()
    fig.savefig(f"{A}/viab_equilibrio.png")
    plt.close(fig)


def g_sensibilidade():
    precos = [14.90, 19.90, 24.90, 29.90, 39.90, 49.90, 59.90]
    ns = [equilibrio(FIXOS, p) for p in precos]
    fig, ax = plt.subplots(figsize=(7.2, 3.6), dpi=200)
    b = ax.bar([brl(p) for p in precos], ns, color=COR["azul"])
    for r, n in zip(b, ns):
        ax.text(r.get_x() + r.get_width() / 2, n + .15, str(n), ha="center")
    ax.set_xlabel("Assinatura mensal por imóvel")
    ax.set_ylabel("Imóveis para empatar")
    ax.grid(axis="y", alpha=.25)
    fig.tight_layout()
    fig.savefig(f"{A}/viab_sensibilidade.png")
    plt.close(fig)


def g_cenarios():
    nomes = [c[0] for c in CENARIOS]
    vidas = [1 / c[3] for c in CENARIOS]
    ltvs = [margem(c[1], c[4]) * av.IMOVEIS_POR_CLIENTE * (1 / c[3]) for c in CENARIOS]
    fig, ax = plt.subplots(figsize=(7.2, 3.4), dpi=200)
    cores = [COR["laranja"], COR["azul"], COR["verde"]]
    b = ax.bar(nomes, ltvs, color=cores)
    for r, v, vd in zip(b, ltvs, vidas):
        ax.text(r.get_x() + r.get_width() / 2, v + 15, f"{brl(v, 0)}\n({vd:.0f} meses)", ha="center", fontsize=9)
    ax.set_ylabel("Margem acumulada por cliente (R$)")
    ax.set_ylim(0, max(ltvs) * 1.25)
    ax.grid(axis="y", alpha=.25)
    fig.tight_layout()
    fig.savefig(f"{A}/viab_cenarios.png")
    plt.close(fig)


def g_fluxo():
    fig, ax = plt.subplots(figsize=(7.6, 3.5), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 46)
    ax.axis("off")
    caixas = [
        (2, 26, 20, 14, "Coleta", "Reservas, bloqueios,\npagamentos, anúncios,\ntelemetria anônima"),
        (28, 26, 20, 14, "Modelagem", "PostgreSQL + Flyway\n(22 migrations)\nsnapshot imutável"),
        (54, 26, 20, 14, "Tratamento", "Agregação, recorte de\njanela de 90 dias,\nanonimização"),
        (78, 26, 20, 14, "Saída", "Dashboard (Chart.js),\nmapas de calor,\nCSV/NDJSON"),
        (28, 4, 20, 14, "Contexto de IA", "ContextoDadosService\n(mín. 3 anúncios)"),
        (54, 4, 20, 14, "Serviço de IA", "Groq / Llama 3.3 70B\nvia RestClient"),
        (78, 4, 20, 14, "Decisão", "Gestor revisa e\nconfirma o preço"),
    ]
    for x, y, w, h, t, s in caixas:
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.4", fc="#EEF3F8", ec=COR["azul"]))
        ax.text(x + w / 2, y + h - 2.6, t, ha="center", fontweight="bold", fontsize=9)
        ax.text(x + w / 2, y + h / 2 - 2.2, s, ha="center", va="center", fontsize=7.4)
    setas = [((22.5, 33), (27.5, 33)), ((48.5, 33), (53.5, 33)), ((74.5, 33), (77.5, 33)),
             ((38, 25.4), (38, 18.6)), ((48.5, 11), (53.5, 11)), ((74.5, 11), (77.5, 11))]
    for (x1, y1), (x2, y2) in setas:
        ax.annotate("", (x2, y2), (x1, y1), arrowprops=dict(arrowstyle="->", color=COR["cinza"]))
    fig.tight_layout()
    fig.savefig(f"{A}/viab_fluxo_dados.png")
    plt.close(fig)


g_equilibrio()
g_sensibilidade()
g_cenarios()
g_fluxo()

# --------------------------------------------------------------------------
# Documento
# --------------------------------------------------------------------------
styles = get_styles()
reset_counters()
story = []
body = lambda t: story.append(Paragraph(t, styles["Body"]))
h1 = lambda t: story.append(Paragraph(t, styles["H1"]))
h2 = lambda t: story.append(Paragraph(t, styles["H2"]))


def tabela(titulo, cab, linhas, larguras, fonte="elaborado pelos autores (2026)", centro=None):
    story.append(CondPageBreak(55 * mm))  # evita titulo do quadro orfao no fim da pagina
    dados = [[Paragraph(c, styles["CellHeader"]) for c in cab]]
    for l in linhas:
        dados.append([Paragraph(str(c), styles["Cell"]) for c in l])
    story.extend(quadro(styles, titulo, quadro_table(dados, [w * mm for w in larguras],
                                                      header_align_center=centro or []), fonte))
    story.append(Spacer(1, 6))


TITULO = "SmartRent B2B — Viabilidade e Impacto do MVP"
SUBTITULO = "Inteligência de dados, inteligência artificial e análise crítica do produto mínimo viável"

story += capa(styles, TITULO, SUBTITULO)
story += folha_de_rosto(styles, TITULO, SUBTITULO,
    "Documento elaborado para a Avaliação de Inteligência de Dados da disciplina de Projeto "
    "Aplicado IV, do curso de Tecnologia em Análise e Desenvolvimento de Sistemas do UniSENAI.")
story += resumo_page(styles,
    "Este documento analisa a viabilidade técnica, econômica e de impacto do produto mínimo "
    "viável (MVP) do SmartRent B2B, plataforma de gestão de reservas e sugestão de preço de "
    "diárias para pequenos anfitriões da Grande Florianópolis. Descreve como os dados são "
    "modelados, tratados e analisados, quais indicadores o sistema entrega e como o serviço de "
    "inteligência artificial (Llama 3.3 70B, via Groq) foi integrado à arquitetura e "
    "alimentado com os dados da própria plataforma, preservando a privacidade por agregação "
    "mínima. A análise econômica é parametrizada e reproduzível: com as hipóteses adotadas, o "
    f"ponto de equilíbrio é de {N_BASE} imóveis pagantes, e o custo de inteligência artificial "
    "é desprezível frente à receita. Conclui-se que o MVP é tecnicamente viável e "
    "economicamente plausível, mas que a viabilidade comercial depende de validação de campo, "
    "para a qual são propostos hipóteses, métricas e critérios de decisão. São discutidos "
    "ainda os riscos, os limites do sistema e o alinhamento ao Objetivo de Desenvolvimento "
    "Sustentável 8.",
    "viabilidade de MVP; inteligência de dados; precificação por IA; indicadores; "
    "Objetivo de Desenvolvimento Sustentável 8")
story += sumario_page(styles)

# ---------------- 1
h1("1 INTRODUÇÃO")
h2("1.1 Contexto e problema")
body("Anfitriões independentes e pequenos gestores de imóveis de temporada na Grande Florianópolis "
     "costumam definir a diária por intuição e acompanhar ocupação e receita em planilhas ou aplicativos "
     "de mensagens. Isso gera preços desalinhados com a sazonalidade, dias ociosos fora da alta "
     "temporada e risco de reservas sobrepostas. Plataformas de revenue management existem, mas são "
     "pensadas para operações maiores, com custo e complexidade incompatíveis com quem administra de "
     "um a cinco imóveis.")
h2("1.2 Objetivos")
body("O objetivo geral é avaliar, antes da implementação completa do projeto, se o MVP do SmartRent B2B "
     "é viável e que impacto inicial se espera dele. Os objetivos específicos são: (a) demonstrar como os "
     "dados são modelados, tratados e analisados e quais indicadores são entregues; (b) descrever a "
     "integração do serviço de inteligência artificial à arquitetura; (c) estimar a viabilidade "
     "econômica com um modelo parametrizado e reproduzível; (d) identificar riscos e limites; e "
     "(e) propor um plano de validação com critérios objetivos de decisão.")
h2("1.3 Método e convenções")
body("A análise combina três fontes: o código-fonte e os testes do repositório (evidência medida), as "
     "decisões registradas nas ADRs (evidência documental) e um modelo econômico com hipóteses "
     "explícitas. <b>Convenção importante:</b> tudo o que é afirmado sobre o sistema pode ser "
     "verificado no repositório; já os valores de preço, adesão e rotatividade de clientes são "
     "<b>hipóteses a validar</b> com anfitriões reais e não foram medidos. O modelo está em "
     "<i>scripts/analise_viabilidade.py</i>, e este documento é regenerado a partir dele, de modo "
     "que alterar uma hipótese atualiza quadros e gráficos.")

# ---------------- 2
h1("2 O MVP E O CICLO DE DADOS")
h2("2.1 Escopo entregue")
body("O MVP vai além do planejado inicialmente (autenticação, imóveis, reservas e sugestão de preço): "
     "inclui fluxo completo de anúncios com máquina de estados, reserva do cliente com política de "
     "cancelamento e reembolso, calendário com bloqueio de datas, SmartChat entre gestor e cliente, "
     "página de perfil com exclusão de dados, painel de administração com mapas de calor e moderação "
     "de denúncias. A suíte automatizada conta 725 testes (JUnit 5, Mockito, <i>@WebMvcTest</i> e "
     "<i>@DataJpaTest</i>) executados sem falhas, e há integração contínua no GitHub Actions.")
h2("2.2 Modelagem dos dados")
body("O esquema relacional em PostgreSQL é versionado por 22 migrations Flyway, e o Hibernate apenas "
     "valida o mapeamento (ADR-002). Decisões de modelagem com efeito direto na qualidade da análise:")
tabela("Decisões de modelagem que sustentam a análise de dados",
       ["Decisão", "Efeito na análise"],
       [["Snapshot imutável de preço, taxas, termos e política em cada reserva",
         "O histórico de receita não muda se o gestor alterar o preço depois; a diária praticada é fiel ao momento da venda."],
        ["Estados de reserva explícitos (pendente, confirmada, concluída, três tipos de cancelamento)",
         "Permite separar ocupação real, receita bruta, estornos e taxa de cancelamento sem ambiguidade."],
        ["Bloqueios de datas em tabela própria",
         "Datas bloqueadas saem do denominador da ocupação, evitando subestimar o desempenho."],
        ["Regra de conflito <i>dataInicio &lt; :fim AND dataFim &gt; :inicio</i>",
         "Garante que noites ocupadas nunca sejam contadas duas vezes."],
        ["Histórico de sugestões de preço e origem do valor (IA ou manual)",
         "Base para, no futuro, comparar sugestão e resultado."],
        ["Tabela de eventos de uso anônimos (sem usuário, IP, texto ou cookies)",
         "Análise de comportamento e mapas de calor sem dado pessoal (ADR-009)."]],
       [62, 92], fonte="repositório do projeto, ADR-002, ADR-003 e ADR-009")
h2("2.3 Tratamento e análise")
body("O tratamento ocorre no servidor, em Java, com agregações testadas em memória. O "
     "<i>EstatisticasService</i> filtra os dados do gestor autenticado e calcula, por mês: reservas, "
     "cancelamentos, noites ocupadas, receita bruta, reembolsos processados e receita líquida; no total, "
     "taxa de ocupação (noites ocupadas sobre noites disponíveis, descontados os bloqueios) e ticket "
     "médio. Reserva pendente não conta como ocupação nem receita, e reserva cancelada depois de paga "
     "permanece na receita bruta com o estorno descontado à parte. A data de referência vem do relógio "
     "da plataforma em horário de Brasília, nunca do navegador.")
story.append(figura(styles, f"{A}/viab_fluxo_dados.png",
                    "ciclo de dados do MVP, da coleta à decisão do gestor", 150, 3.5 / 7.6))
h2("2.4 Dashboards e indicadores")
tabela("Indicadores entregues pelo sistema",
       ["Público", "Indicador", "Onde"],
       [["Gestor", "Ocupação, receita bruta e líquida, reembolsos, ticket médio, reservas e cancelamentos por mês, imóveis por status",
         "Dashboard e página de estatísticas (Chart.js sobre canvas)"],
        ["Gestor", "Calendário de três meses por imóvel, com reservas e bloqueios", "Dashboard"],
        ["Administrador", "Visão geral da plataforma, uso do site por página e dia, tempo de permanência, picos por dia da semana e hora, "
                          "distribuição por dispositivo, funil de uso", "Painel de administração"],
        ["Administrador", "Mapas de calor de cliques sobre a própria página, por dispositivo", "Painel de administração"],
        ["Analista", "Exportação dos eventos em CSV e NDJSON", "Painel de administração"]],
       [24, 84, 46], fonte="repositório do projeto (EstatisticasService, TelemetriaAnaliseService)")

# ---------------- 3
h1("3 INTELIGÊNCIA ARTIFICIAL INTEGRADA À ARQUITETURA")
h2("3.1 Serviço de sugestão de preço")
body("A sugestão é obtida por <i>GroqPricingSuggestionProvider</i>, que chama a API compatível com a da OpenAI "
     "(Groq, modelo Llama 3.3 70B) pelo <i>RestClient</i> síncrono do Spring, com tempo limite configurável. "
     "A resposta é exigida em JSON com valor e justificativa de até 300 caracteres; o valor é validado "
     "contra uma faixa (R$ 1,00 a R$ 100.000,00) e a justificativa é sanitizada. A abstração "
     "<i>PricingSuggestionService</i> é uma interface pequena, de modo que trocar de provedor exige "
     "apenas outra implementação, sem tocar nas regras de pré-publicação.")
h2("3.2 Dados da plataforma alimentando a IA")
body("A sugestão não depende só do conhecimento geral do modelo. O <i>ContextoDadosService</i> agrega, nos "
     "últimos 90 dias, o desempenho do próprio imóvel (ocupação e diária média praticada, ponderada "
     "pelas noites) e o panorama dos anúncios semelhantes no ar, do mesmo tipo e cidade (diária anunciada "
     "mediana e faixa, ocupação e diária média praticada). Esses números entram no prompt, e o modelo é "
     "instruído a calibrar o valor por eles e a citar na justificativa quais usou. Assim, a mesma "
     "base que alimenta os indicadores do gestor também alimenta o serviço de IA.")
tabela("Contexto enviado à IA e o que nunca é enviado",
       ["Enviado", "Não enviado"],
       [["Tipo, bairro, cidade/UF, área, quartos, banheiros, vagas, capacidade, mínimo de diárias, comodidades e data de hoje (sazonalidade)",
         "Título e descrição (texto livre), logradouro, número, CEP, coordenadas, links e qualquer dado do gestor ou de hóspedes"],
        ["Agregados do imóvel e do mercado: ocupação, diária média, mediana e faixa de preço",
         "Preço ou ocupação de um anúncio individual de terceiros"]],
       [77, 77], fonte="repositório do projeto (GroqPricingSuggestionProvider e ContextoDadosService)")
h2("3.3 Privacidade por agregação mínima")
body("Para que o panorama de mercado não permita identificar um concorrente, ele só é informado quando há "
     "ao menos <b>três</b> outros anúncios semelhantes; abaixo disso, nada é enviado. O contexto "
     "nunca inclui nome, e-mail, endereço exato ou texto livre. O gestor sempre revisa e confirma o "
     "valor: a IA apenas sugere, e a origem (IA ou manual) fica registrada.")
h2("3.4 Tolerância a falhas e testes")
body("Qualquer falha (chave ausente, rede, erro HTTP, tempo esgotado, resposta fora do formato, valor "
     "inválido) vira uma exceção com mensagem própria para o gestor, e o fluxo degrada para a definição "
     "manual do preço. Se a consulta ao contexto de dados falhar, a IA segue sem esse contexto. Os testes usam "
     "servidor HTTP simulado e <i>mocks</i>, sem rede nem custo, e cobrem o recorte da janela, a "
     "mediana, a regra de amostra mínima e a presença dos dados no prompt.")
h2("3.5 Limites da abordagem")
body("O contexto depende de volume: em plataforma nova, com poucos anúncios e reservas, ele quase sempre "
     "estará vazio, e a sugestão volta a depender do conhecimento geral do modelo. A calibração é "
     "textual (o modelo recebe os números), não um modelo estatístico treinado; e ainda não há "
     "comparação automática entre o preço sugerido e o resultado obtido. Esses pontos orientam o "
     "plano de evolução da seção 9.")

# ---------------- 4
h1("4 VIABILIDADE TÉCNICA")
tabela("Avaliação da viabilidade técnica",
       ["Pergunta", "Resposta e evidência"],
       [["O stack comporta o MVP?", "Sim. Java 17, Spring Boot 3, PostgreSQL com Flyway, front em JavaScript puro e Chart.js; arquitetura em camadas (controller, service, repository)."],
        ["Os dados são consistentes?", "Sim. Restrições no banco, snapshot imutável por reserva, bloqueio de conflito de datas e concorrência testada."],
        ["A IA é confiável o bastante?", "Parcialmente. Falhas degradam com segurança para o preço manual, mas a qualidade da sugestão ainda não foi medida contra resultados reais."],
        ["Está testado?", "Sim. 725 testes automatizados sem falhas e integração contínua. O front tem apenas verificações estáticas (lacuna conhecida)."],
        ["A segurança é adequada?", "Em boa parte. Auditoria de 21 achados corrigidos (ADR-008), JWT sem segredo padrão, cifra em repouso do chat, limites de login e cabeçalhos de segurança."],
        ["A infraestrutura é suficiente?", "Para demonstração, sim; para clientes pagantes, não. O plano gratuito hiberna (primeira visita em cerca de 3 minutos) e tem 512 MB de memória."]],
       [48, 106], fonte="repositório do projeto, STATUS_PROJETO.md e ADR-008")
body("<b>Conclusão técnica:</b> viável. As pendências relevantes para operar com clientes reais são "
     "hospedagem sem hibernação, e-mail transacional real, gateway de pagamento real, armazenamento "
     "de mídia em objeto e testes automatizados de interface.")

# ---------------- 5
h1("5 VIABILIDADE ECONÔMICA")
h2("5.1 Premissas")
tabela("Hipóteses do modelo econômico (a validar com anfitriões reais)",
       ["Parâmetro", "Valor adotado", "Observação"],
       [["Assinatura por imóvel/mês", brl(av.ASSINATURA_POR_IMOVEL), "Hipótese de monetização do BMC; não implementada no MVP"],
        ["Imóveis por cliente", str(av.IMOVEIS_POR_CLIENTE), "Segmento de 1 a 5 imóveis"],
        ["Sugestões por imóvel/mês", str(av.SUGESTOES_POR_IMOVEL_MES), "Teto: cerca de uma por dia"],
        ["Tokens por sugestão", str(av.TOKENS_POR_SUGESTAO), "Prompt (inclui o contexto de dados) mais resposta"],
        ["Custo do modelo", f"US$ {av.USD_POR_MILHAO_TOKENS:.2f} por milhão de tokens".replace(".", ","), f"Câmbio de R$ {av.BRL_POR_USD:.2f}".replace(".", ",")],
        ["Custos fixos mensais", brl(FIXOS), f"Hospedagem paga básica ({brl(av.INFRA_FIXA_MES, 0)}) e outros ({brl(av.OUTROS_FIXOS_MES, 0)})"],
        ["Taxa do gateway", pct(av.TAXA_GATEWAY, 2), "Sobre a assinatura"],
        ["Cancelamento mensal de clientes", pct(av.CHURN_MENSAL), f"Vida média de cerca de {VIDA:.0f} meses"]],
       [48, 38, 68], fonte="scripts/analise_viabilidade.py")
h2("5.2 Economia unitária e ponto de equilíbrio")
tabela("Economia unitária por imóvel/mês (cenário base)",
       ["Item", "Valor"],
       [["Receita bruta", brl(PRECO)],
        ["(–) Taxa do gateway", brl(PRECO * av.TAXA_GATEWAY)],
        ["(–) Custo de IA", brl(custo_ia())],
        ["<b>Margem de contribuição</b>", f"<b>{brl(M_BASE)} ({pct(M_BASE / PRECO)})</b>"],
        ["Ponto de equilíbrio", f"{N_BASE} imóveis pagantes (cerca de {-(-N_BASE // av.IMOVEIS_POR_CLIENTE)} clientes)"],
        ["Margem acumulada por cliente (vida média)", brl(LTV_CLIENTE)]],
       [92, 62], fonte="cálculo a partir de scripts/analise_viabilidade.py")
story.append(figura(styles, f"{A}/viab_equilibrio.png", "ponto de equilíbrio no cenário base", 150, 3.8 / 7.2))
body(f"O custo de IA por imóvel é de cerca de {brl(custo_ia())} ao mês, ou {brl(custo_ia(av.SUGESTOES_POR_IMOVEL_MES * 10))} "
     "se o uso for dez vezes maior: a inteligência artificial não é o item que pressiona a margem. O que "
     "pesa são os custos fixos e, sobretudo, a aquisição e a retenção de clientes.")
h2("5.3 Sensibilidade e cenários")
story.append(figura(styles, f"{A}/viab_sensibilidade.png", "imóveis necessários para o equilíbrio conforme a assinatura", 150, 3.6 / 7.2))
linhas = []
for nome, preco, fixos, churn, sug in CENARIOS:
    n = equilibrio(fixos, preco, sug)
    ltv = margem(preco, sug) * av.IMOVEIS_POR_CLIENTE / churn
    linhas.append([nome, brl(preco), brl(fixos, 0), pct(churn), str(sug), str(n), brl(ltv, 0)])
tabela("Cenários pessimista, base e otimista",
       ["Cenário", "Assinatura", "Fixos/mês", "Churn mensal", "Sugestões/imóvel", "Equilíbrio (imóveis)", "Margem por cliente"],
       linhas, [22, 22, 20, 20, 24, 24, 22], centro=[1, 2, 3, 4, 5, 6], fonte="cálculo a partir de scripts/analise_viabilidade.py")
story.append(figura(styles, f"{A}/viab_cenarios.png", "margem acumulada por cliente em cada cenário", 150, 3.4 / 7.2))
h2("5.4 Leitura crítica")
body(f"O equilíbrio de {N_BASE} imóveis é uma conta de custos fixos e <b>ignora o custo de aquisição de "
     "clientes e o tempo da equipe</b>, que no MVP não é remunerado. Com a margem acumulada por cliente "
     f"de cerca de {brl(LTV_CLIENTE, 0)}, o custo de aquisição precisa ficar bem abaixo disso (como regra de bolso, "
     "no máximo um terço). Mesmo o cenário pessimista fecha o equilíbrio com poucos imóveis, o que "
     "indica que o risco econômico real está em conseguir e manter clientes, não em custo de operação. "
     "Como a cobrança não está implementada (o gateway é simulado), a <b>disposição a pagar é a hipótese "
     "menos comprovada do projeto</b>.")

# ---------------- 6
h1("6 IMPACTO INICIAL ESPERADO")
tabela("Dimensões de impacto e como medi-las",
       ["Dimensão", "Impacto esperado", "Como medir"],
       [["Operacional", "Menos tempo decidindo preço e conferindo calendário; fim das reservas sobrepostas pela verificação de conflito.",
         "Cronometrar cinco anfitriões na publicação de um anúncio com preço, antes e depois."],
        ["Econômico (anfitrião)", "Preço mais aderente à sazonalidade e ao mercado local pode elevar a receita por noite disponível. Efeito ainda não medido.",
         "Comparar a receita por noite disponível (RevPAR) de três meses antes e depois."],
        ["Social (ODS 8)", "Leva a um microempreendedor uma ferramenta de gestão antes restrita a operações maiores (metas 8.2, 8.3 e 8.9).",
         "Número de anfitriões independentes ativos; proporção com preço revisado na alta temporada."],
        ["Dados e privacidade", "Telemetria anônima que respeita DNT/GPC, chat cifrado em repouso, minimização de dados enviados à IA e amostra mínima no contexto de mercado.",
         "Auditoria periódica; revisão jurídica da LGPD ainda pendente."],
        ["Ambiental", "Sem impacto direto relevante; a hospedagem em nuvem compartilhada e o uso de IA por demanda mantêm o consumo baixo.",
         "Não aplicável nesta fase."]],
       [28, 70, 56], fonte="elaborado pelos autores (2026), com base no BMC e nas ADRs")
body("Em termos sociais, o MVP responde a uma assimetria: grandes operadores têm equipes de precificação, "
     "enquanto o pequeno anfitrião decide sozinho. O benefício só se confirma se a sugestão for "
     "compreensível e confiável para quem a usa, por isso a justificativa é obrigatória e o valor "
     "permanece sempre editável.")

# ---------------- 7
h1("7 RISCOS")
tabela("Riscos que podem inviabilizar o MVP",
       ["Risco", "Prob.", "Impacto", "Resposta"],
       [["Anfitrião desconfiar da sugestão de IA", "Média", "Alto", "Justificativa visível e valor sempre editável (implementado)."],
        ["Sugestão sem lastro em dado local gerar preço ruim", "Média", "Alto", "Contexto de mercado e histórico no prompt; falta medir sugestão contra resultado."],
        ["Dependência do provedor de IA (preço, limite, termos)", "Média", "Médio", "Interface <i>PricingSuggestionService</i>; trocar o provedor é uma classe."],
        ["Concorrência com preço inteligente embutido (plataformas de reserva e ferramentas de revenue management)", "Alta", "Alto", "Foco no nicho local e na gestão própria de reservas; validar que o anfitrião quer sair das planilhas."],
        ["Hospedagem gratuita hibernando diante de cliente pagante", "Alta", "Médio", "Plano pago antes do primeiro cliente."],
        ["Conformidade com a LGPD (telemetria, avisos, decisões automatizadas de moderação)", "Média", "Alto", "Revisão jurídica registrada como pendência nas ADR-009 e ADR-010."],
        ["Baixo volume de dados deixar o contexto de IA vazio", "Alta", "Médio", "Aceitar o degradê para o conhecimento geral do modelo e acumular histórico no piloto."]],
       [60, 14, 16, 64], centro=[1, 2], fonte="elaborado pelos autores (2026)")

# ---------------- 8
h1("8 PENSAMENTO CRÍTICO E INOVAÇÃO")
h2("8.1 Visão sistêmica")
body("O produto conecta quatro camadas que se reforçam: a gestão de reservas gera dados confiáveis; os "
     "indicadores devolvem esses dados ao gestor; o serviço de IA usa os mesmos dados para sugerir "
     "preço; e a telemetria anônima mostra se o produto é de fato usado. Do lado social e econômico, "
     "o ganho para o anfitrião depende da confiança na sugestão; do lado regulatório, depende de "
     "privacidade e transparência; do lado técnico, depende de custo de operação baixo. As ADRs "
     "registram essas trocas.")
h2("8.2 Decisões conscientes e alternativas descartadas")
tabela("Decisões com trocas explícitas",
       ["Decisão", "Alternativa descartada", "Motivo"],
       [["Banco relacional para análise", "Ferramentas de Big Data (Spark, data warehouse)", "O volume atual é tratável em PostgreSQL; seria sobre-engenharia. Evolução prevista por rollup e particionamento."],
        ["IA generativa com contexto textual", "Modelo estatístico próprio treinado", "Sem histórico suficiente para treinar; o contexto textual entrega valor desde o primeiro dado."],
        ["Telemetria anônima própria", "Ferramenta de análise de terceiros com identificação", "Evita dado pessoal, cookies e transferência a terceiros."],
        ["Gestor confirma cada preço", "Aplicação automática da sugestão", "A IA só sugere; mantém responsabilidade e confiança do usuário."],
        ["Pagamento simulado no MVP", "Gateway real desde o início", "Reduz risco e escopo; a disposição a pagar é validada antes de cobrar."]],
       [44, 46, 64], fonte="ADR-002, ADR-006, ADR-009 e documentação do projeto")
h2("8.3 Limites assumidos")
body("Os limites estão declarados para evitar conclusões inflacionadas: os números econômicos são hipóteses; "
     "o impacto no anfitrião não foi medido; a sugestão por IA ainda não é comparada ao resultado; o "
     "contexto de mercado só aparece com amostra mínima; o front não tem testes automatizados de "
     "interface; e há pendências jurídicas (política de privacidade, consentimento, decisões "
     "automatizadas). Assumi-los é parte da análise crítica e orienta o plano a seguir.")

# ---------------- 9
h1("9 VALIDAÇÃO E PRÓXIMOS PASSOS")
h2("9.1 Hipóteses e critérios de decisão")
tabela("Hipóteses do piloto e métricas",
       ["Hipótese", "Métrica", "Meta"],
       [["H1 – O anfitrião usa a sugestão de preço", "Proporção de anúncios com sugestão confirmada", "Pelo menos metade dos anúncios, por 60 dias"],
        ["H2 – A sugestão economiza tempo", "Tempo para publicar anúncio com preço", "Redução perceptível frente ao método atual"],
        ["H3 – O anfitrião paga pelo serviço", "Disposição a pagar declarada e conversão", "Pelo menos R$ 20 por imóvel ao mês"],
        ["H4 – O preço sugerido melhora o resultado", "Receita por noite disponível antes e depois", "Variação positiva em três meses"]],
       [58, 52, 44], fonte="elaborado pelos autores (2026)")
body("<b>Regra de decisão após o piloto com cinco anfitriões:</b> <i>segue</i> se pelo menos três usarem a "
     "sugestão na metade dos anúncios e aceitarem pagar o valor mínimo; <i>ajusta</i> se usarem, mas "
     "não pagarem (reposicionar preço ou valor entregue); <i>para ou pivota</i> se menos de dois "
     "usarem a sugestão, o que indicaria que o preço por intuição não é a dor principal.")
h2("9.2 Evolução técnica priorizada")
body("(a) Registrar sugestão, preço praticado e ocupação resultante para medir se a sugestão melhora a "
     "receita por noite disponível e, com volume, avaliar um modelo estatístico complementar; "
     "(b) funil de uso ordenado por sessão na telemetria; (c) e-mail transacional e gateway de pagamento "
     "reais; (d) hospedagem sem hibernação; (e) testes automatizados de interface; (f) revisão "
     "jurídica e política de privacidade.")

# ---------------- 10
h1("10 CONCLUSÃO")
body("O MVP do SmartRent B2B é tecnicamente viável: os dados são modelados com integridade, tratados e "
     "apresentados em indicadores úteis, e o serviço de IA está integrado à arquitetura, alimentado "
     "pelos dados da própria plataforma, com privacidade preservada e degradação segura. "
     "Economicamente é plausível: o custo de IA é marginal e o equilíbrio exige poucos imóveis pagantes, "
     "embora esse resultado dependa de hipóteses ainda não validadas e ignore o custo de aquisição. "
     "A viabilidade comercial só se confirma em campo; por isso o documento entrega hipóteses, "
     "métricas e critérios objetivos de decisão, em vez de uma promessa. A recomendação é seguir para o "
     "piloto com anfitriões reais, com hospedagem estável, e usar seus resultados para decidir entre "
     "seguir, ajustar ou pivotar.")

# ---------------- Anexo
h1("APÊNDICE A – CORRESPONDÊNCIA COM OS REQUISITOS DA AVALIAÇÃO")
tabela("Requisitos da avaliação e evidências",
       ["Requisito", "Evidência"],
       [["Modelar, tratar e analisar dados", "Seções 2.2 e 2.3; migrations Flyway, EstatisticasService e telemetria."],
        ["Dashboards e indicadores", "Seção 2.4; Dashboard do gestor e painel de administração."],
        ["Desenvolver ou integrar serviço de IA", "Seção 3; provedor Groq (Llama 3.3 70B) e ContextoDadosService."],
        ["Integrar os recursos à arquitetura do produto", "Seções 2.3 e 3; camadas do Spring, interface de provedor, CI e Docker."],
        ["Analisar viabilidade e impacto inicial do MVP", "Seções 4, 5 e 6; modelo reproduzível em scripts/analise_viabilidade.py."],
        ["Colaboração e gestão de tempo", "Histórico de commits e pull requests, STATUS_PROJETO.md e Cronograma (Documento 2)."],
        ["Visão sistêmica e pensamento crítico", "Seções 5.4, 7 e 8."],
        ["Soluções singulares e sustentáveis", "Seção 3.2 (IA ancorada nos dados locais com amostra mínima) e seção 8.2."]],
       [60, 94], fonte="elaborado pelos autores (2026)")

refs = [
    "BRASIL. <b>Lei nº 13.709, de 14 de agosto de 2018</b>. Lei Geral de Proteção de Dados Pessoais (LGPD). "
    "Brasília, DF: Presidência da República, 2018.",
    "ORGANIZAÇÃO DAS NAÇÕES UNIDAS. <b>Objetivo de Desenvolvimento Sustentável 8</b>: trabalho decente e "
    "crescimento econômico. Nova York: ONU, 2015.",
    "OSTERWALDER, A.; PIGNEUR, Y. <b>Business Model Generation</b>: inovação em modelos de negócios. "
    "Rio de Janeiro: Alta Books, 2011.",
    "GROQ. <b>Groq API Documentation</b>. [S. l.]: Groq, [2024]. Disponível em: https://console.groq.com/docs. "
    "Acesso em: 20 out. 2026.",
    "SPRING. <b>Spring Framework Reference</b>: RestClient. [S. l.]: VMware, [2024]. Disponível em: "
    "https://docs.spring.io/spring-framework/reference/. Acesso em: 20 out. 2026.",
    "UNISENAI. <b>Repositório do projeto SmartRent B2B</b>: código-fonte, testes, ADRs e "
    "STATUS_PROJETO.md. Florianópolis, 2026.",
]
story += referencias_page(styles, refs)

doc = new_doc(os.path.join(BASE, "out", "07_ANALISE_DE_VIABILIDADE.pdf"), "Análise de Viabilidade e Impacto do MVP",
              use_toc_template=True)
hf = make_header_footer_condicional()
doc.multiBuild(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 07_ANALISE_DE_VIABILIDADE.pdf")
