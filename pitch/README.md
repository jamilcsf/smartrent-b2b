# Pitch SmartRent — Produto & Protótipo

Deck de 27 slides no mesmo formato do pitch de referência (EcoWatt), com os dados do SmartRent.
Arquivo único, sem dependências externas: `smartrent_pitch.html`.

## Como abrir

Duplo clique no arquivo, ou servindo a pasta:

```bash
npx --yes http-server . -p 5500
```

Depois acesse `http://127.0.0.1:5500/smartrent_pitch.html`. Navegação: setas, Espaço, PageUp/PageDown, Home/End,
os botões do canto ou `#/N` na URL (0 a 26). Para PDF, imprima pelo navegador (um slide por página).

## Roteiro

| Slides | Conteúdo |
|---|---|
| 1–2 | Capa e roteiro |
| 3–5 | Bloco 1: estado do produto e telas |
| 6–11 | **Demos animadas**: ciclo do anúncio, calendário com conflito de datas, SmartChat borrando contatos, preço da IA × preço fixo, **painel de admin com mapa de calor** e **moderação (da denúncia à decisão)** |
| 12–15 | Casos de teste, resultados, **gráficos de qualidade** (cobertura, achados por severidade, cobertura por componente) e falhas |
| 16–20 | Bloco 2: stack, ambiente, camada de dados (com a telemetria de uso) e Big Data planejado |
| 21–23 | Bloco 3: proposta de valor, impacto, modelo e público-alvo |
| 24–25 | Bloco 4: vendas, canais e monetização |
| 26–27 | Checklist de cobertura e encerramento |

As animações são SVG, CSS e JavaScript puro (sem biblioteca). Começam ao entrar no slide, têm o botão **Repetir** e
vão direto ao estado final se o sistema pedir menos movimento (`prefers-reduced-motion`).

## Origem dos números (confira antes de apresentar)

Tudo vem do repositório, sem valores inventados:

- 720 testes e 85,3% de cobertura de linhas (JaCoCo 0.8.12 rodado como plugin avulso, pois não está no `pom.xml`), medidos em 08/10/2026:
  `docs/STATUS_PROJETO.md`. Para reconfirmar: `mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`
  (relatório em `target/site/jacoco`). Antes da banca, rode de novo. A rodada anterior (06/10) era 671 testes e 84,0%.
- 21 achados de segurança (1 crítico, 5 altos, 9 médios, 6 baixos): `docs/adr/ADR-008-seguranca-chat-uploads.md`.
- 18 telas (`src/main/resources/static`), 24 controladores, 28 entidades/tabelas e migrations V1–V22.
- Negócio (SaaS por assinatura por imóvel, público-alvo, ODS 8): `docs/documentacao-tecnica.md`, capítulos 1 e 2.

## Pontos que o deck assume com transparência

- Monetização é **hipótese**: cobrança não implementada, pagamento da reserva é simulado.
- **Validação em campo** com anfitrião real aparece como *Pendente* no checklist (slide 26). Se já foi feita, atualize.
- Big Data (processamento distribuído) **não é usado hoje**; o que existe é a **coleta** de eventos anônimos de uso e a exportação CSV/NDJSON (slides 4, 19 e 20). O resto do slide 20 é visão de evolução (roadmap V2).
- As demos 10 e 11 (mapa de calor e moderação) são **simulações com dados fictícios** do fluxo real; o mapa de calor do produto usa os eventos coletados.
- A privacidade da telemetria, os avisos e as decisões automatizadas aguardam revisão jurídica (ADR-009 e ADR-010).
- A curva de preço do slide 9 usa **dados ilustrativos** (identificados no próprio slide), não medições. Gráficos de qualidade usam números reais (JaCoCo e ADR-008).
- Pendências abertas citadas: rotação da senha do banco no Supabase e id numérico em reserva, notificação e imóvel.
