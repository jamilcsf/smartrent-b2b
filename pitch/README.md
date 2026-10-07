# Pitch SmartRent — Produto & Protótipo

Deck de 25 slides no mesmo formato do pitch de referência (EcoWatt), com os dados do SmartRent.
Arquivo único, sem dependências externas: `smartrent_pitch.html`.

## Como abrir

Duplo clique no arquivo, ou servindo a pasta:

```bash
npx --yes http-server . -p 5500
```

Depois acesse `http://127.0.0.1:5500/smartrent_pitch.html`. Navegação: setas, Espaço, PageUp/PageDown, Home/End,
os botões do canto ou `#/N` na URL (0 a 24). Para PDF, imprima pelo navegador (um slide por página).

## Roteiro

| Slides | Conteúdo |
|---|---|
| 1–2 | Capa e roteiro |
| 3–5 | Bloco 1: estado do produto e telas |
| 6–9 | **Demos animadas**: ciclo do anúncio, calendário com conflito de datas, SmartChat borrando contatos, preço da IA × preço fixo |
| 10–13 | Casos de teste, resultados, **gráficos de qualidade** (cobertura, achados por severidade, cobertura por componente) e falhas |
| 14–18 | Bloco 2: stack, ambiente, camada de dados e Big Data planejado |
| 19–21 | Bloco 3: proposta de valor, impacto, modelo e público-alvo |
| 22–23 | Bloco 4: vendas, canais e monetização |
| 24–25 | Checklist de cobertura e encerramento |

As animações são SVG, CSS e JavaScript puro (sem biblioteca). Começam ao entrar no slide, têm o botão **Repetir** e
vão direto ao estado final se o sistema pedir menos movimento (`prefers-reduced-motion`).

## Origem dos números (confira antes de apresentar)

Tudo vem do repositório, sem valores inventados:

- 671 testes e 84,0% de cobertura de linhas (JaCoCo): `docs/STATUS_PROJETO.md`, rodada de 06/10/2026.
  Rode `mvn -B test` para reconfirmar antes da banca.
- 21 achados de segurança (1 crítico, 5 altos, 9 médios, 6 baixos): `docs/adr/ADR-008-seguranca-chat-uploads.md`.
- 15 telas (`src/main/resources/static`), 20 controladores, 25 entidades/tabelas e migrations V1–V20.
- Negócio (SaaS por assinatura por imóvel, público-alvo, ODS 8): `docs/documentacao-tecnica.md`, capítulos 1 e 2.

## Pontos que o deck assume com transparência

- Monetização é **hipótese**: cobrança não implementada, pagamento da reserva é simulado.
- **Validação em campo** com anfitrião real aparece como *Pendente* no checklist (slide 24). Se já foi feita, atualize.
- Big Data **não é usado hoje**; o slide 18 é visão de evolução (roadmap V2).
- A curva de preço do slide 9 usa **dados ilustrativos** (identificados no próprio slide), não medições. Gráficos de qualidade usam números reais (JaCoCo e ADR-008).
- Pendências abertas citadas: rotação da senha do banco no Supabase e id numérico em reserva, notificação e imóvel.
