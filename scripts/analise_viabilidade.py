"""Modelo de viabilidade economica do MVP do SmartRent B2B.

Os valores abaixo sao HIPOTESES a validar com anfitrioes reais, nao fatos
medidos. O script existe para que a analise seja reproduzivel: mude um
parametro, rode de novo e veja o efeito no ponto de equilibrio.

Uso:  python scripts/analise_viabilidade.py
"""
from math import ceil

# --- Hipoteses (editar aqui) -------------------------------------------------
ASSINATURA_POR_IMOVEL = 29.90      # R$/mes por imovel gerenciado (hipotese)
IMOVEIS_POR_CLIENTE = 3            # segmento: 1 a 5 imoveis
SUGESTOES_POR_IMOVEL_MES = 30      # cada gestor pede sugestao ~1x/dia (teto)
TOKENS_POR_SUGESTAO = 700          # prompt + resposta (aprox.)
USD_POR_MILHAO_TOKENS = 0.70       # ordem de grandeza de modelo 70B hospedado
BRL_POR_USD = 5.50
INFRA_FIXA_MES = 85.00             # plano pago basico de app + banco (hipotese)
OUTROS_FIXOS_MES = 40.00           # dominio, e-mail transacional, monitoramento
TAXA_GATEWAY = 0.0399              # sobre a assinatura (cartao), hipotese
CHURN_MENSAL = 0.06                # cancelamento mensal de clientes (hipotese)


def custo_ia_por_imovel() -> float:
    tokens = SUGESTOES_POR_IMOVEL_MES * TOKENS_POR_SUGESTAO
    return tokens / 1_000_000 * USD_POR_MILHAO_TOKENS * BRL_POR_USD


def margem_por_imovel() -> float:
    return ASSINATURA_POR_IMOVEL * (1 - TAXA_GATEWAY) - custo_ia_por_imovel()


def equilibrio_imoveis(fixos: float, margem: float) -> int:
    return ceil(fixos / margem)


def main() -> None:
    fixos = INFRA_FIXA_MES + OUTROS_FIXOS_MES
    margem = margem_por_imovel()
    n = equilibrio_imoveis(fixos, margem)
    print("== Unit economics (por imovel/mes) ==")
    print(f"Receita bruta ........ R$ {ASSINATURA_POR_IMOVEL:7.2f}")
    print(f"Taxa do gateway ...... R$ {ASSINATURA_POR_IMOVEL * TAXA_GATEWAY:7.2f}")
    print(f"Custo de IA .......... R$ {custo_ia_por_imovel():7.2f}")
    print(f"Margem de contribuicao R$ {margem:7.2f} "
          f"({margem / ASSINATURA_POR_IMOVEL:.0%})")
    print()
    print("== Ponto de equilibrio ==")
    print(f"Custos fixos ......... R$ {fixos:7.2f}/mes")
    print(f"Imoveis pagantes ..... {n}  (~{ceil(n / IMOVEIS_POR_CLIENTE)} clientes)")
    print(f"Vida media do cliente  {1 / CHURN_MENSAL:.1f} meses (churn {CHURN_MENSAL:.0%})")
    print()
    print("== Sensibilidade: preco da assinatura x imoveis para empatar ==")
    for preco in (19.90, 29.90, 39.90, 49.90):
        m = preco * (1 - TAXA_GATEWAY) - custo_ia_por_imovel()
        print(f"R$ {preco:5.2f} -> {equilibrio_imoveis(fixos, m):3d} imoveis")
    print()
    print("== Sensibilidade: custo de IA se o uso for 10x maior ==")
    original = SUGESTOES_POR_IMOVEL_MES
    globals()["SUGESTOES_POR_IMOVEL_MES"] = original * 10
    print(f"Custo de IA/imovel ... R$ {custo_ia_por_imovel():.2f} "
          f"(margem {margem_por_imovel() / ASSINATURA_POR_IMOVEL:.0%})")


if __name__ == "__main__":
    main()
