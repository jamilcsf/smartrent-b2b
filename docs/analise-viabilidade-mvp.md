# Análise de Viabilidade e Impacto Inicial do MVP — SmartRent B2B

**Disciplina:** Projeto Aplicado IV — AV Inteligência de Dados · **Data:** 2026-10-20

> **Como ler este documento.** Separamos o que é **medido no repositório** (código, testes, migrations) do que é **hipótese a validar** (preço, adesão, churn). Os números econômicos saem de `scripts/analise_viabilidade.py`, que o leitor pode rodar e alterar. Nenhum deles foi medido com clientes reais.

## 1. Problema e hipótese central

Anfitriões e pequenos gestores da Grande Florianópolis (1 a 5 imóveis) definem a diária por intuição e acompanham ocupação e receita em planilhas. Hipótese do MVP: **uma plataforma que une reservas, indicadores de ocupação/receita e sugestão de preço por IA reduz o tempo operacional e a subjetividade do preço**, com custo de operação baixo o bastante para caber numa assinatura acessível.

## 2. Viabilidade técnica — **viável, com evidência**

| Pergunta | Evidência no projeto |
|---|---|
| Os dados foram modelados e tratados? | Modelo relacional em PostgreSQL com 22 migrations Flyway (`src/main/resources/db/migration`), snapshot imutável de preço/termos em cada reserva, regra `dataInicio < :fim AND dataFim > :inicio` contra conflito de datas. |
| Há análise e indicadores? | `EstatisticasService` calcula ocupação (descontando bloqueios), receita bruta e líquida de reembolsos, ticket médio e cancelamentos por mês; exibidos em Chart.js no Dashboard do gestor. `TelemetriaAnaliseService` + `/admin.html` entregam uso do site e mapas de calor (telemetria anônima, ADR-009). |
| Há IA integrada à arquitetura? | `GroqPricingSuggestionProvider` chama a Groq (Llama 3.3 70B) por `RestClient` síncrono, com timeout, validação da faixa de valor e queda para edição manual; o resultado alimenta o formulário de anúncio e o histórico de sugestões. Só dados do imóvel vão ao provedor (sem titular, endereço exato ou texto livre). |
| A IA falhar quebra o produto? | Não: o fluxo degrada para preço manual; os testes de IA usam mock e não dependem de rede. |
| Está testado? | 671 testes automatizados (JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest`), segundo o `STATUS_PROJETO.md`; CI no GitHub Actions. |
| Opera em infraestrutura barata? | Sim, mas com limite: no plano gratuito do Render o serviço hiberna (primeira visita ≈ 3 min). Aceitável para demonstração, **não** para cliente pagante. |

**IA ancorada nos dados da plataforma.** O `ContextoDadosService` agrega, nos últimos 90 dias, a ocupação e a diária média praticada do próprio imóvel e o panorama dos anúncios semelhantes (mesmo tipo e cidade: diária anunciada mediana e faixa, ocupação e diária praticada). Esses números entram no prompt e a IA cita na justificativa os que usou. Privacidade: só agregados; o panorama exige no mínimo 3 outros anúncios (senão não é informado) e nunca inclui nome, e-mail ou endereço. Se a consulta falhar, a IA segue sem o contexto. **Limite honesto:** com pouco histórico (plataforma nova) o contexto quase sempre estará vazio e a sugestão volta a depender do conhecimento geral do modelo; a calibração só melhora com volume. Ainda não há comparação automática entre sugestão e resultado.

## 3. Viabilidade econômica — **plausível, condicionada à validação de preço**

Hipóteses (editáveis no script): assinatura de R$ 29,90 por imóvel/mês; 3 imóveis por cliente; 30 sugestões por imóvel/mês; ~700 tokens por sugestão; custos fixos de R$ 125/mês (hospedagem paga básica + domínio + e-mail + monitoramento); taxa de gateway de 3,99%; churn de 6% ao mês.

| Indicador | Resultado do modelo |
|---|---|
| Custo de IA por imóvel/mês | ≈ R$ 0,08 (≈ R$ 0,81 se o uso for 10×) |
| Margem de contribuição por imóvel | ≈ R$ 28,63 (96%) |
| Ponto de equilíbrio (custos fixos) | **5 imóveis pagantes ≈ 2 clientes** |
| Sensibilidade ao preço | R$ 19,90 → 7 imóveis · R$ 39,90 → 4 · R$ 49,90 → 3 |

**Leitura crítica.** O custo de IA é desprezível; **o risco econômico não está na infraestrutura, e sim na aquisição e retenção**. O equilíbrio de 5 imóveis ignora o custo de adquirir clientes e o tempo da equipe (não remunerado no MVP). Com churn de 6%, a vida média é de ~17 meses e a margem acumulada por cliente de 3 imóveis é de ≈ R$ 1.430 (16,7 × 3 × R$ 28,63), **se** o churn se confirmar; o custo de aquisição precisa ficar bem abaixo disso (como regra de bolso, no máximo um terço). A monetização **não está implementada** no MVP (pagamento é gateway simulado), então a disposição a pagar é a hipótese menos comprovada do projeto.

## 4. Impacto inicial esperado

| Dimensão | Impacto | Como medir |
|---|---|---|
| **Operacional** | Menos tempo decidindo preço e conferindo calendário; sem overbooking pelo bloqueio de conflito de datas. | Tempo para publicar anúncio com preço (cronometrar 5 anfitriões, antes × depois). |
| **Econômico (anfitrião)** | Preço mais aderente à sazonalidade pode elevar receita por noite ocupada; efeito **não medido**. | Comparar RevPAR (receita por noite disponível) de 3 meses antes e depois. |
| **Social / ODS 8** | Ferramenta de nível "revenue management" ao pequeno anfitrião local (metas 8.2, 8.3, 8.9). | Nº de anfitriões independentes ativos; % com preço revisado na alta temporada. |
| **Dados / privacidade** | Telemetria anônima (sem usuário, IP ou cookies; respeita DNT/GPC), chat cifrado em repouso, minimização no envio à IA. | Auditoria periódica; revisão jurídica LGPD ainda pendente. |

## 5. Riscos que podem inviabilizar o MVP

| Risco | Prob. | Impacto | Resposta |
|---|:--:|:--:|---|
| Anfitrião não aceitar preço sugerido por IA (desconfiança) | Média | Alto | Mostrar justificativa de até 300 caracteres e deixar o valor sempre editável (já implementado). |
| Sugestão sem lastro em dado local gerar preço ruim | Média | Alto | Contexto de mercado e histórico já no prompt (seção 2); falta medir sugestão × resultado (seção 6). |
| Dependência do provedor de IA (preço, limite, mudança de termos) | Média | Médio | `PricingSuggestionService` é interface; trocar de provedor é uma classe. |
| Concorrência (Airbnb com preço inteligente embutido, PriceLabs) | Alta | Alto | Foco no nicho local e na gestão própria de reservas; validar que o anfitrião quer sair de planilhas. |
| Hospedagem gratuita hibernando perante cliente pagante | Alta | Médio | Plano pago antes do primeiro cliente (R$ 85/mês no modelo). |
| LGPD (telemetria, avisos, decisões automatizadas de moderação) | Média | Alto | Revisão jurídica registrada como pendência nas ADR-009 e ADR-010. |

## 6. Decisão de viabilidade e próximos passos

**Decisão: viável para seguir como MVP**, com a ressalva de que viabilidade **comercial** só se confirma com dados de campo. Critérios objetivos para o "segue / ajusta / para" após o piloto:

1. **Segue** se ≥ 3 de 5 anfitriões piloto usarem a sugestão em pelo menos metade dos anúncios por 60 dias e aceitarem pagar ≥ R$ 20/imóvel.
2. **Ajusta** se usarem mas não pagarem: reposicionar preço ou valor entregue.
3. **Para / pivota** se menos de 2 de 5 usarem a sugestão: o problema "preço por intuição" não é a dor principal.

Evolução técnica priorizada: (a) registrar sugestão × preço praticado × ocupação resultante para medir se a sugestão melhora o RevPAR; (b) funil de uso ordenado por sessão na telemetria; (c) SMTP e gateway reais; (d) plano de hospedagem sem hibernação.

## 7. Matriz: requisitos da AV × evidências

| Requisito da professora | Onde está evidenciado |
|---|---|
| Modelar, tratar e analisar dados | DER e 22 migrations; `EstatisticasService`; telemetria e exportação CSV/NDJSON (ADR-009) |
| Dashboards e indicadores | `/dashboard.html` e `/estatisticas.html` (gestor), `/admin.html` com mapas de calor (admin) |
| Modelo ou serviço de IA | `GroqPricingSuggestionProvider` (Llama 3.3 70B) + `ContextoDadosService` (dados da plataforma no prompt), `PrecificacaoLoteService`, decisões automáticas do SmartChat com revisão humana (ADR-010) |
| Integração à arquitetura | Camadas `controller/service/repository`, interface `PricingSuggestionService`, CI, Docker, Render |
| Viabilidade e impacto do MVP | **Este documento** + `scripts/analise_viabilidade.py` |
| Colaboração e prazos | Histórico de commits e PRs, `STATUS_PROJETO.md` e cronograma em `docs/relatorios-fonte` |
| Visão sistêmica e pensamento crítico | Seções 2 (limite técnico), 3 (leitura crítica) e 5 (riscos); ADRs 002–010 registram decisões e alternativas descartadas |
