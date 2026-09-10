# -*- coding: utf-8 -*-
"""Gera o diagrama do Business Model Canvas do SmartRent B2B.

Diferença da v1: em vez de estimar quantos caracteres cabem por linha
(o que causou texto vazando da caixa), aqui cada linha é medida de
verdade via renderer.get_window_extent() antes de ser aceita.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
import os
BASE = os.path.dirname(os.path.abspath(__file__))

OUT = os.path.join(BASE, "assets")
os.makedirs(OUT, exist_ok=True)

TEAL = "#0F766E"
TEAL_DARK = "#0B3B36"
SAND_DARK = "#B96E00"
SLATE = "#1F2937"
WHITE = "#FFFFFF"

plt.rcParams["font.family"] = "DejaVu Sans"

FIGW, FIGH, DPI = 15.5, 9.6, 170
W, H = 1500, 940

fig, ax = plt.subplots(figsize=(FIGW, FIGH), dpi=DPI)
fig.subplots_adjust(left=0, right=1, top=1, bottom=0)
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.axis("off")
fig.canvas.draw()  # garante que exista um renderer válido para medição
renderer = fig.canvas.get_renderer()


def data_width_to_px(dx):
    """Converte uma largura em unidades de dado para pixels de tela usando a
    transformação real dos eixos — não uma razão figsize/dpi aproximada,
    que ignora as margens padrão do subplot e sub-estima o quanto de
    texto cabe (foi o que vazou texto das caixas na primeira versão)."""
    p0 = ax.transData.transform((0, 0))
    p1 = ax.transData.transform((dx, 0))
    return abs(p1[0] - p0[0])


def text_width_px(s, fontsize, bold):
    t = ax.text(0, -1000, s, fontsize=fontsize, fontweight="bold" if bold else "normal")
    bbox = t.get_window_extent(renderer=renderer)
    t.remove()
    return bbox.width


def wrap_to_width(s, fontsize, max_width_units, bold=False):
    max_px = data_width_to_px(max_width_units)
    words = s.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if text_width_px(trial, fontsize, bold) <= max_px or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


TOP = 50
GRID_BOTTOM = 690
BOTTOM_ROW_Y = 690
BOTTOM_ROW_H = 210

c1_x, c1_w = 20, 250
c2_x, c2_w = 285, 250
c3_x, c3_w = 550, 360
c4_x, c4_w = 925, 250
c5_x, c5_w = 1190, 250


def cell(x, y, w, h, title, items, title_bg=TEAL, title_fs=10.2, item_fs=8.6, item_gap=4.5,
         line_h=12.6):
    box = Rectangle((x, y), w, h, linewidth=1.1, edgecolor=TEAL_DARK, facecolor=WHITE, zorder=2)
    ax.add_patch(box)

    title_lines = wrap_to_width(title, title_fs, w - 20, bold=True)
    th = 22 + line_h * len(title_lines)
    head = Rectangle((x, y), w, th, linewidth=0, facecolor=title_bg, zorder=3)
    ax.add_patch(head)
    ty0 = y + th / 2 - (line_h / 2) * (len(title_lines) - 1)
    for i, line in enumerate(title_lines):
        ax.text(x + w / 2, ty0 + i * line_h, line, ha="center", va="center", fontsize=title_fs,
                fontweight="bold", color=WHITE, zorder=4)

    ty = y + th + 12
    bullet_indent = 16
    text_w = w - bullet_indent - 14
    for it in items:
        wrapped = wrap_to_width(it, item_fs, text_w, bold=False)
        ax.text(x + 9, ty, "-", fontsize=item_fs, color=SAND_DARK, zorder=4, fontweight="bold")
        for line in wrapped:
            ax.text(x + bullet_indent, ty, line, ha="left", va="top", fontsize=item_fs,
                    color=SLATE, zorder=4)
            ty += line_h
        ty += item_gap
    bottom_used = ty - item_gap
    if bottom_used > y + h + 2:
        print(f"AVISO: bloco '{title}' excede a caixa em {bottom_used - (y+h):.0f} unidades")
    return bottom_used


cell(c1_x, TOP, c1_w, GRID_BOTTOM - TOP, "Parcerias-chave", [
    "Groq — IA generativa (Llama 3.3)",
    "Supabase — PostgreSQL gerenciado",
    "Render — hospedagem do backend",
    "Vercel — hospedagem do frontend",
    "GitHub Actions — integração contínua",
    "SEBRAE / associações de turismo locais (V2)",
    "OTAs (Airbnb, Booking) para sincronização iCal (V2)",
], title_bg=TEAL_DARK)

mid2_y = TOP + (GRID_BOTTOM - TOP) / 2
cell(c2_x, TOP, c2_w, mid2_y - TOP - 6, "Atividades-chave", [
    "Desenvolvimento e manutenção da plataforma",
    "Curadoria do motor de precificação (ajuste do prompt e da regra de contingência)",
    "Aquisição e onboarding de anfitriões",
    "Suporte ao cliente",
], title_bg=TEAL)
cell(c2_x, mid2_y + 6, c2_w, GRID_BOTTOM - mid2_y - 6, "Recursos principais", [
    "Motor de IA (Groq / Llama 3.3)",
    "Plataforma Spring Boot + PostgreSQL",
    "Equipe de desenvolvimento (3 integrantes)",
    "Documentação viva do projeto (ADRs, STATUS_PROJETO.md)",
], title_bg=TEAL)

cell(c3_x, TOP, c3_w, GRID_BOTTOM - TOP, "Proposta de valor", [
    "Trava automática de conflito de reservas (elimina overbooking)",
    "Precificação preditiva por sazonalidade e microgeografia",
    "Cálculo de contingência quando a IA falha — nunca fica sem preço sugerido",
    "Origem de cada sugestão registrada e auditável (IA ou regra determinística)",
    "Painel executivo com indicadores de ocupação",
    "Ferramenta acessível ao pequeno anfitrião, sem a complexidade de um PMS corporativo",
], title_bg=SAND_DARK, title_fs=11.4, item_fs=9.0)

mid4_y = TOP + (GRID_BOTTOM - TOP) / 2
cell(c4_x, TOP, c4_w, mid4_y - TOP - 6, "Relacionamento", [
    "Autoatendimento (self-service)",
    "Onboarding assistido para o perfil menos técnico",
    "Suporte por canal digital",
    "Conteúdo educativo sobre precificação de temporada (V2)",
], title_bg=TEAL)
cell(c4_x, mid4_y + 6, c4_w, GRID_BOTTOM - mid4_y - 6, "Canais", [
    "Plataforma web (cadastro direto)",
    "Indicação entre anfitriões da região",
    "Parcerias com imobiliárias locais",
    "Redes sociais e marketing regionalizado (V2)",
], title_bg=TEAL)

cell(c5_x, TOP, c5_w, GRID_BOTTOM - TOP, "Segmentos de clientes", [
    "Pequenos anfitriões (1 a 5 imóveis próprios)",
    "Administradores de carteira (imóveis de terceiros)",
    "Beneficiário indireto: o hóspede (não é cliente pagante)",
], title_bg=TEAL_DARK)

half = (c5_x + c5_w - c1_x) / 2
cell(c1_x, BOTTOM_ROW_Y + 14, half - 7, BOTTOM_ROW_H - 14, "Estrutura de custos", [
    "Infraestrutura em nuvem (Supabase, Render, Vercel)",
    "Consumo da API de IA generativa, proporcional ao número de sugestões geradas",
    "Equipe de desenvolvimento e suporte",
    "Ferramentas de integração contínua (GitHub Actions)",
    "Aquisição de clientes: hoje orgânica, sem custo de mídia paga (V2 em avaliação)",
], title_bg=SAND_DARK)
cell(c1_x + half + 7, BOTTOM_ROW_Y + 14, half - 7, BOTTOM_ROW_H - 14, "Fontes de receita", [
    "Assinatura mensal por imóvel gerenciado (SaaS)",
    "Plano diferenciado para administradores de carteira",
    "Comissão sobre o valor das reservas processadas (V2)",
    "Taxa de onboarding assistido para clientes de menor afinidade técnica (V2)",
], title_bg=SAND_DARK)

ax.text(20, 22, "Business Model Canvas — SmartRent B2B", fontsize=13.5, fontweight="bold",
        color=TEAL_DARK)
ax.set_ylim(H - 10, -10)
plt.savefig(f"{OUT}/business_model_canvas.png", facecolor="white")
plt.close()
print("OK")
