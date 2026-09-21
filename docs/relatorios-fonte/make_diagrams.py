# -*- coding: utf-8 -*-
"""Gera os diagramas (PNG) usados no Relatório Técnico do SmartRent B2B."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Ellipse, FancyArrowPatch, Circle, Rectangle
from matplotlib.lines import Line2D
import matplotlib.font_manager as fm
import os
BASE = os.path.dirname(os.path.abspath(__file__))

OUT = os.path.join(BASE, "assets")
os.makedirs(OUT, exist_ok=True)

TEAL = "#0F766E"
TEAL_DARK = "#0B3B36"
SAND = "#F5A623"
SAND_DARK = "#B96E00"
SLATE = "#1F2937"
LIGHT = "#F8FAFC"
LINE = "#334155"
WHITE = "#FFFFFF"

plt.rcParams["font.family"] = "DejaVu Sans"


def new_fig(w, h):
    fig, ax = plt.subplots(figsize=(w, h), dpi=170)
    ax.set_xlim(0, w * 10)
    ax.set_ylim(0, h * 10)
    ax.axis("off")
    ax.invert_yaxis()
    return fig, ax


def class_box(ax, x, y, w, h, title, attrs, title_bg=TEAL, note=None):
    box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.4,rounding_size=3",
                          linewidth=1.4, edgecolor=TEAL_DARK, facecolor=WHITE, zorder=2)
    ax.add_patch(box)
    th = 15
    head = Rectangle((x, y), w, th, linewidth=0, facecolor=title_bg, zorder=3)
    ax.add_patch(head)
    ax.plot([x, x + w], [y + th, y + th], color=TEAL_DARK, linewidth=1.2, zorder=4)
    ax.text(x + w / 2, y + th / 2, title, ha="center", va="center",
            fontsize=10.5, fontweight="bold", color=WHITE, zorder=5)
    ty = y + th + 8
    for a in attrs:
        ax.text(x + 8, ty, a, ha="left", va="top", fontsize=8.3, color=SLATE, zorder=5,
                 family="monospace")
        ty += 8.6
    if note:
        ax.text(x + w / 2, y + h - 4, note, ha="center", va="bottom", fontsize=7.3,
                style="italic", color="#64748B", zorder=5)
    return (x, y, w, h)


def link(ax, p1, p2, side1, side2, label1="", label2="", dashed=False, diamond_at=None):
    x1, y1, w1, h1 = p1
    x2, y2, w2, h2 = p2
    pts = {
        "r": (x1 + w1, y1 + h1 / 2), "l": (x1, y1 + h1 / 2),
        "t": (x1 + w1 / 2, y1), "b": (x1 + w1 / 2, y1 + h1),
    }
    pts2 = {
        "r": (x2 + w2, y2 + h2 / 2), "l": (x2, y2 + h2 / 2),
        "t": (x2 + w2 / 2, y2), "b": (x2 + w2 / 2, y2 + h2),
    }
    a = pts[side1]
    b = pts2[side2]
    style = (0, (4, 3)) if dashed else "-"
    ax.plot([a[0], b[0]], [a[1], b[1]], color=LINE, linewidth=1.2, linestyle=style, zorder=1)
    if label1:
        ax.text(a[0] + (8 if side1 == "r" else -8 if side1 == "l" else 0),
                 a[1] - 6, label1, fontsize=7.5, color=SLATE, ha="left" if side1 == "r" else "right")
    if label2:
        ax.text(b[0] + (-8 if side2 == "l" else 8 if side2 == "r" else 0),
                 b[1] - 6, label2, fontsize=7.5, color=SLATE, ha="right" if side2 == "l" else "left")
    if diamond_at == 1:
        ax.scatter([a[0]], [a[1]], marker="D", s=55, color=SAND, zorder=6, edgecolor=TEAL_DARK)


# ---------------------------------------------------------------
# 1) DIAGRAMA DE CLASSES
# ---------------------------------------------------------------
fig, ax = new_fig(13, 8.6)
W, H = 130, 10 * 8.6

usuario = class_box(ax, 20, 20, 190, 140, "Usuario",
    ["- id: Long", "- nome: String", "- email: String", "- senhaHash: String",
     "- papel: PapelUsuario", "- telefone: String", "- ativo: boolean",
     "- dataCriacao: LocalDateTime"], title_bg=TEAL)

imovel = class_box(ax, 330, 20, 210, 168, "Imovel",
    ["- id: Long", "- titulo: String", "- tipoImovel: TipoImovel",
     "- endereco: Endereco", "- capacidadeHospedes: Integer",
     "- numeroQuartos: Integer", "- numeroBanheiros: Integer",
     "- metragemQuadrada: Integer", "- vagasGaragem: Integer",
     "- valorDiariaBase: BigDecimal", "- comodidades: List<String>",
     "- ativo: boolean"], title_bg=TEAL)

endereco = class_box(ax, 620, 20, 190, 108, "Endereco «embeddable»",
    ["- logradouro/numero/bairro", "- cidade: String", "- estado: String (UF)",
     "- cep: String", "- latitude: BigDecimal", "- longitude: BigDecimal"],
     title_bg=SAND_DARK, note="fator de microgeografia (IA)")

reserva = class_box(ax, 330, 250, 230, 178, "Reserva",
    ["- id: Long", "- hospedeNome/Email/Telefone", "- dataCheckin: LocalDate",
     "- dataCheckout: LocalDate", "- valorTotal: BigDecimal",
     "- status: StatusReserva", "- origem: OrigemReserva",
     "- versao: Long  «@Version»",
     ], title_bg=TEAL)

sugestao = class_box(ax, 610, 250, 230, 178, "SugestaoPreco",
    ["- id: Long", "- dataReferencia: LocalDate", "- valorBase: BigDecimal",
     "- valorSugerido: BigDecimal", "- fatorSazonalidade: BigDecimal",
     "- fatorMicrogeografia: BigDecimal", "- justificativaIa: String",
     "- modeloIaUtilizado: String",
     "- origemCalculo: OrigemCalculoPreco"], title_bg=TEAL)

link(ax, usuario, imovel, "r", "l", "1", "0..*")
link(ax, imovel, endereco, "r", "l", diamond_at=1)
link(ax, imovel, reserva, "b", "t", "1", "0..*")
link(ax, imovel, sugestao, "r", "t", "1", "0..*")

ax.text(20, 8, "Diagrama de Classes — Domínio SmartRent B2B  (setas: associação · losango: composição)",
        fontsize=10, color=TEAL_DARK, fontweight="bold")
ax.set_xlim(0, 870)
ax.set_ylim(450, -22)
plt.savefig(f"{OUT}/diagrama_classes.png", facecolor="white")
plt.close()

# ---------------------------------------------------------------
# 2) DIAGRAMA DE CASOS DE USO
# ---------------------------------------------------------------
fig, ax = new_fig(13, 9)


def actor(ax, x, y, label):
    cx, cy = x, y
    ax.add_patch(Circle((cx, cy - 22), 9, facecolor=WHITE, edgecolor=SLATE, linewidth=1.4, zorder=5))
    ax.plot([cx, cx], [cy - 13, cy + 18], color=SLATE, linewidth=1.4, zorder=5)
    ax.plot([cx - 14, cx + 14], [cy - 2, cy - 2], color=SLATE, linewidth=1.4, zorder=5)
    ax.plot([cx, cx - 12], [cy + 18, cy + 34], color=SLATE, linewidth=1.4, zorder=5)
    ax.plot([cx, cx + 12], [cy + 18, cy + 34], color=SLATE, linewidth=1.4, zorder=5)
    ax.text(cx, cy + 44, label, ha="center", va="top", fontsize=8.6, fontweight="bold", color=SLATE)
    return (cx, cy)


def usecase(ax, x, y, w, h, label):
    e = Ellipse((x, y), w, h, facecolor="#E6FFFA", edgecolor=TEAL_DARK, linewidth=1.3, zorder=3)
    ax.add_patch(e)
    ax.text(x, y, label, ha="center", va="center", fontsize=8, color=TEAL_DARK, wrap=True, zorder=4)
    return (x, y, w, h)


# fronteira do sistema
sys_box = FancyBboxPatch((150, 48), 620, 700, boxstyle="round,pad=0.4,rounding_size=4",
                          linewidth=1.6, edgecolor=TEAL, facecolor="#FBFEFE", linestyle="--", zorder=1)
ax.add_patch(sys_box)
ax.text(150 + 310, 64, "SmartRent B2B", ha="center", fontsize=11, fontweight="bold", color=TEAL_DARK)

a1 = actor(ax, 60, 380, "Anfitrião /\nAdministrador\nde Imóveis")
a2 = actor(ax, 840, 200, "Motor de IA\n(Groq · Llama 3.3)")
a3 = actor(ax, 840, 560, "Hóspede\n(dado indireto)")

uc_login = usecase(ax, 320, 90, 190, 60, "Autenticar-se\n(herdado do PA III)")
uc_cad_im = usecase(ax, 320, 190, 190, 60, "Gerenciar Imóveis\n(CRUD)")
uc_cad_res = usecase(ax, 320, 300, 200, 65, "Gerenciar Reservas\n(CRUD)")
uc_conflito = usecase(ax, 620, 300, 170, 60, "Verificar Conflito\nde Datas")
uc_sugestao = usecase(ax, 320, 420, 210, 65, "Gerar Sugestão\nde Preço")
uc_fallback = usecase(ax, 620, 430, 170, 60, "Aplicar Fallback\nDeterminístico")
uc_dash = usecase(ax, 320, 540, 200, 60, "Consultar Dashboard\n(Chart.js)")
uc_cancel = usecase(ax, 320, 640, 200, 60, "Cancelar / Concluir\nReserva")

for uc in [uc_login, uc_cad_im, uc_cad_res, uc_sugestao, uc_dash, uc_cancel]:
    ax.plot([a1[0] + 14, uc[0] - uc[2] / 2], [a1[1] + 10, uc[1]], color=SLATE, linewidth=0.9, zorder=2)

ax.plot([uc_cad_res[0] + uc_cad_res[2] / 2, uc_conflito[0] - uc_conflito[2] / 2],
        [uc_cad_res[1], uc_conflito[1]], color=TEAL_DARK, linewidth=1.1, linestyle=(0, (4, 2)), zorder=2)
ax.annotate("«include»", xy=((uc_cad_res[0] + uc_conflito[0]) / 2, (uc_cad_res[1] + uc_conflito[1]) / 2 - 10),
            fontsize=7.3, color=TEAL_DARK, ha="center", style="italic")

ax.plot([uc_sugestao[0] + uc_sugestao[2] / 2, uc_fallback[0] - uc_fallback[2] / 2],
        [uc_sugestao[1], uc_fallback[1]], color=TEAL_DARK, linewidth=1.1, linestyle=(0, (4, 2)), zorder=2)
ax.annotate("«extend» se a IA falhar", xy=((uc_sugestao[0] + uc_fallback[0]) / 2, (uc_sugestao[1] + uc_fallback[1]) / 2 - 10),
            fontsize=7.3, color=TEAL_DARK, ha="center", style="italic")

ax.plot([a2[0] - 14, uc_sugestao[0] + uc_sugestao[2] / 2], [a2[1] + 10, uc_sugestao[1]], color=SLATE, linewidth=0.9, zorder=2)
ax.plot([a2[0] - 14, uc_fallback[0] + uc_fallback[2] / 2], [a2[1] + 30, uc_fallback[1]], color=SLATE, linewidth=0.9, zorder=2)
ax.plot([a3[0] - 14, uc_cad_res[0] + uc_cad_res[2] / 2], [a3[1] + 10, uc_cad_res[1] + 10], color=SLATE, linewidth=0.9, zorder=2)

ax.text(20, 12, "Diagrama de Casos de Uso — SmartRent B2B", fontsize=10, color=TEAL_DARK, fontweight="bold")
ax.set_xlim(0, 900)
ax.set_ylim(760, -30)
plt.savefig(f"{OUT}/diagrama_casos_de_uso.png", facecolor="white")
plt.close()


# ---------------------------------------------------------------
# 3) DIAGRAMA DE SEQUÊNCIA — Reserva com trava de conflito
# ---------------------------------------------------------------
def sequence_diagram(filename, title, lifelines, messages, height=6.6):
    n = len(lifelines)
    fig, ax = plt.subplots(figsize=(13, height), dpi=170)
    ax.set_xlim(0, 130)
    top = 20
    ax.set_ylim(top + len(messages) * 9 + 10, 0)
    ax.axis("off")
    ax.text(2, 6, title, fontsize=11, color=TEAL_DARK, fontweight="bold")
    xs = [12 + i * (108 / (n - 1)) for i in range(n)]
    for x, label in zip(xs, lifelines):
        box = FancyBboxPatch((x - 13, top - 8), 26, 12, boxstyle="round,pad=0.3,rounding_size=2",
                              linewidth=1.3, edgecolor=TEAL_DARK, facecolor=TEAL, zorder=5)
        ax.add_patch(box)
        ax.text(x, top - 2, label, ha="center", va="center", fontsize=7.6, color="white",
                fontweight="bold", wrap=True, zorder=6)
        ax.plot([x, x], [top + 4, top + len(messages) * 9 + 10], color="#94A3B8",
                 linewidth=1, linestyle=(0, (3, 2)), zorder=1)
    y = top + 12
    for (frm, to, label, kind) in messages:
        x1, x2 = xs[frm], xs[to]
        color = SAND_DARK if kind == "err" else (TEAL_DARK if kind != "return" else "#64748B")
        ls = (0, (4, 2)) if kind == "return" else "-"
        ar = FancyArrowPatch((x1, y), (x2, y), arrowstyle="-|>", mutation_scale=11,
                              linewidth=1.3, color=color, linestyle=ls, zorder=4,
                              shrinkA=2, shrinkB=2)
        ax.add_patch(ar)
        mid = (x1 + x2) / 2
        ax.text(mid, y - 2.2, label, ha="center", va="bottom", fontsize=7.4, color=SLATE, zorder=6)
        y += 9
    plt.tight_layout()
    plt.savefig(f"{OUT}/{filename}", bbox_inches="tight", facecolor="white")
    plt.close()


sequence_diagram(
    "diagrama_sequencia_reserva.png",
    "Diagrama de Sequência — Criação de Reserva com Trava de Conflito",
    ["Anfitrião\n(Frontend)", "Reserva\nController", "Reserva\nService", "Reserva\nRepository", "PostgreSQL\n(Supabase)"],
    [
        (0, 1, "POST /reservas (dados da reserva)", "call"),
        (1, 2, "criarReserva(dto)  @Transactional", "call"),
        (2, 3, "existeConflitoDeDatas(imovelId, checkin, checkout)", "call"),
        (3, 4, "SELECT ... WHERE overlap de datas", "call"),
        (4, 3, "true / false", "return"),
        (3, 2, "existeConflito: boolean", "return"),
        (2, 1, "[se existeConflito] 409 Conflict + reservas conflitantes", "err"),
        (2, 3, "[se não] save(reserva)", "call"),
        (3, 4, "INSERT INTO reservas ...", "call"),
        (1, 0, "201 Created (reserva confirmada)", "return"),
    ],
    height=6.4,
)

sequence_diagram(
    "diagrama_sequencia_precificacao.png",
    "Diagrama de Sequência — Sugestão de Preço com Fallback",
    ["Dashboard\n(Frontend)", "SugestaoPreco\nController", "SugestaoPreco\nService", "Groq API\n(Llama 3.3)", "SugestaoPreco\nRepository"],
    [
        (0, 1, "GET /imoveis/{id}/sugestao-preco?data=", "call"),
        (1, 2, "gerarSugestao(imovelId, data)", "call"),
        (2, 3, "RestClient: prompt c/ sazonalidade + microgeografia", "call"),
        (3, 2, "[timeout/erro] exceção", "err"),
        (2, 2, "aplica fallback determinístico (regra de negócio)", "call"),
        (2, 4, "save(SugestaoPreco, origemCalculo=FALLBACK)", "call"),
        (2, 1, "SugestaoPrecoResponse", "return"),
        (1, 0, "200 OK (valor sugerido + justificativa)", "return"),
    ],
    height=6.0,
)

# ---------------------------------------------------------------
# 4) GANTT — CRONOGRAMA
# ---------------------------------------------------------------
fig, ax = plt.subplots(figsize=(13, 5.2), dpi=170)
tasks = [
    ("Semana 1 — Fundação", "Setup Maven/Spring, entidades JPA, repositórios, schema (Flyway) + validação Postgres", 0, 7, TEAL),
    ("Semana 2 — Domínio de Negócio", "DTOs, camada Service (trava de conflito, @Transactional), Controllers REST", 7, 7, TEAL),
    ("Semana 2-3 — Motor de IA", "Integração Groq/Llama 3.3 (RestClient) + fallback determinístico auditável", 11, 6, SAND_DARK),
    ("Semana 3 — Frontend", "Telas (HTML+Tailwind+JS), CRUD Imóveis/Reservas, Dashboard Chart.js", 14, 8, TEAL),
    ("Semana 4 — Qualidade", "Testes JUnit5/Mockito, CI (GitHub Actions), deploy Render/Vercel", 22, 5, TEAL),
    ("Semana 4 — Entrega", "Relatório final, ensaio de apresentação, documentação de banca", 27, 3, SAND_DARK),
]
ax.set_xlim(0, 38)
ax.set_ylim(-1, len(tasks))
for i, (name, desc, start, dur, color) in enumerate(tasks):
    y = len(tasks) - i - 1
    ax.barh(y, dur, left=start, height=0.55, color=color, edgecolor=TEAL_DARK, linewidth=1)
    if dur >= 6:
        ax.text(start + 0.3, y, name, va="center", ha="left", fontsize=8.6, color="white", fontweight="bold", zorder=5)
        ax.text(start + dur + 0.4, y, f"dias {start+1}-{start+dur}", va="center", ha="left", fontsize=7.6, color=SLATE)
    else:
        ax.text(start + dur + 0.4, y, name, va="center", ha="left", fontsize=8.6, color=TEAL_DARK, fontweight="bold", zorder=5)
        ax.text(start + dur + 0.4, y - 0.32, f"dias {start+1}-{start+dur}", va="center", ha="left", fontsize=7.4, color=SLATE)
ax.set_yticks([])
ax.set_xticks(range(0, 31, 5))
ax.set_xlabel("Dias corridos do MVP (escopo de 30 dias)", fontsize=9)
for spine in ["top", "right", "left"]:
    ax.spines[spine].set_visible(False)
ax.set_title("Cronograma do Projeto — SmartRent B2B (visão macro)", fontsize=11, color=TEAL_DARK, fontweight="bold", loc="left")
ax.grid(axis="x", linestyle=(0, (2, 3)), color="#CBD5E1", zorder=0)
plt.tight_layout()
plt.savefig(f"{OUT}/gantt_cronograma.png", bbox_inches="tight", facecolor="white")
plt.close()

print("Diagramas gerados em", OUT)
print(os.listdir(OUT))
