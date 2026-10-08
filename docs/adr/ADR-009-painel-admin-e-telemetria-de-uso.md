# ADR-009 — Painel de administração e telemetria de uso (mapas de calor)

**Status:** Aceita
**Data:** 2026-10-08
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

O papel `ADMIN` já existia (enum, permissões herdadas do gestor, `isGestor()` no front), mas não havia rota, tela nem
dado que o distinguisse: um administrador só via o painel do gestor. Pediu-se um painel de administração completo e **mapas de
calor do uso**, para saber quais partes do site são mais usadas e alimentar análises de big data.

## Decisões

### 1. Painel `/admin.html` e `/api/admin/**` só para `ADMIN`

- O `SecurityConfig` ganhou `.requestMatchers("/api/admin/**").hasRole("ADMIN")` **antes** da regra do gestor: gestor e cliente
  recebem 403, visitante 401. O front só decide o que mostrar (`Auth.isAdmin()`); quem nega é o servidor.
- Seções: visão geral da plataforma, uso do site, mapas de calor, listas de usuários/imóveis/reservas (busca, filtro e paginação,
  tamanho de página limitado e `%`/`_` da busca escapados) e exportação dos eventos.
- **Única escrita:** ativar/desativar conta (`PATCH /api/admin/usuarios/{id}/ativo`). Desativar derruba as sessões abertas (o filtro JWT
  exige conta ativa). O admin **não** altera a própria conta nem a de outro admin (evita se trancar fora e escalada lateral) e cada
  mudança vai para `auditoria_conta` (`ADMIN_CONTA_DESATIVADA`/`ADMIN_CONTA_ATIVADA`, só o id de quem fez, sem dados pessoais).
- O painel **não** lê o texto do SmartChat nem tem ação sobre pedidos de exclusão de dados: mostra apenas contagens (pedidos em andamento,
  denúncias pendentes). Esses fluxos seguem as ADR-005/006/008.
- Não há criação de admin pela interface (o cadastro público nunca cria `ADMIN`). A promoção é deliberada, por SQL:
  `update usuarios set papel = 'ADMIN' where email = '...';`.

### 2. Telemetria de uso **anônima por desenho** (`eventos_uso`, migration V21)

Um evento por interação: `VISUALIZACAO`, `CLIQUE` (posição + identificador estrutural do elemento), `ROLAGEM` (profundidade máxima, em
degraus de 10%) e `PERMANENCIA` (segundos com a aba visível).

| Coletado | Nunca coletado |
|---|---|
| caminho da página (sem query/fragmento; `/imoveis/123` vira `/imoveis/{id}`) | usuário, e-mail ou qualquer id de pessoa |
| posição do clique (milésimos da largura, pixels, altura do documento) | IP (usado só em memória, para o limite de frequência) |
| alvo do clique: `#id`, `[data-track=x]` ou `tag[href=/caminho]` | texto de elementos, texto digitado, valores de campos |
| papel (`VISITANTE`/`CLIENTE`/`ANFITRIAO`/`ADMIN`) e classe de tela | cookies, `localStorage`, URL completa |
| sorteio de sessão do navegador (`sessionStorage`, vale por aba) | cruzamento com a conta |

- O coletor (`js/telemetria.js`) respeita **Do Not Track** e **Global Privacy Control** (também no servidor), não roda em iframe nem no
  próprio painel e falha em silêncio.
- O endpoint `POST /api/telemetria/eventos` é **público**, então nada que chega é confiado (`TelemetriaService`): lote de até 50
  eventos, limite por origem (60 lotes/min por IP), validação e normalização campo a campo, hora e fuso vindos do servidor, papel vindo do
  token (nunca do corpo). O que não valida é descartado sem erro. O alvo só aceita caracteres estruturais e **nunca começa por `=`, `+`,
  `-` ou `@`** (exportado para planilha, evitaria injeção de fórmula).
- **Retenção:** job diário apaga eventos com mais de `TELEMETRIA_RETENCAO_DIAS` (padrão 180). `TELEMETRIA_HABILITADA=false` desliga a coleta.

### 3. Mapa de calor sobre a **própria página**

O painel carrega a página real num `iframe` do mesmo site (sandbox, sem interação, largura do dispositivo escolhido) e desenha por cima, num
`canvas`, as manchas de cliques agregadas numa grade de 50 colunas × 25 px. Cliques em elementos fixos/grudados (cabeçalho) usam a posição na
janela, não no documento. Os dados são separados por **dispositivo** (computador/tablet/celular), porque o layout muda.
Ao lado: elementos mais clicados da página e até onde se rola.

**Consequência de segurança:** para o iframe funcionar, a CSP passou de `frame-ancestors 'none'` para `'self'` e o `X-Frame-Options` de
`DENY` para `SAMEORIGIN`. Nenhuma outra origem pode emoldurar o site; só ele mesmo. O teste CT1083 foi atualizado.

### 4. Pensado para volume (big data)

- Tabela append-only, com índices só para as consultas do painel e colunas **denormalizadas** na gravação (`dia`, `hora`, `dia_semana`,
  `x_celula`, `y_celula`): as agregações são `GROUP BY` simples em JPQL portável, sem funções de data, e nunca carregam eventos brutos.
- **Exportação** (`GET /api/admin/uso/exportar?formato=csv|ndjson&dias=N`): fluxo em lotes por chave (`id > :ultimo`), sem offset e sem
  manter tudo na memória, para Power BI/Metabase/DuckDB/Spark/pandas. O dicionário de colunas está no próprio painel.
- A grade fica gravada junto do evento bruto (`x_pm`, `y_px`), então mudar o tamanho da célula no futuro é reprocessar, não recoletar.

## Limites e pendências

- **Jurídico/LGPD (pendente, não decidido aqui):** os dados são anônimos e agregáveis, mas a necessidade de aviso/consentimento, o texto da
  política de privacidade e o prazo de retenção definitivo dependem do setor jurídico, como nos demais textos provisórios do projeto.
  Enquanto isso valem DNT/GPC, o prazo de 180 dias e a coleta desligável.
- **Conversão** (reservas criadas ÷ sessões que abriram um anúncio) é aproximada: sem identificador de pessoa não se liga o clique à reserva.
- Os dados de demonstração (`scripts/dados-telemetria-demo.sql`) são **sintéticos** e geram cliques só para desktop; não os confunda com uso real.
- Limite por IP é em memória (uma instância). Atrás de proxy, exige `FORWARD_HEADERS_STRATEGY=framework`; com várias instâncias, trocar
  por limitador distribuído (mesma observação do `LimitadorDeTaxa`).

## Visão Futura (V2)

- Funil ordenado por sessão (catálogo → anúncio → reserva) e coortes; mapa de rolagem e de movimento do mouse.
- Rollup diário pré-agregado e/ou particionamento da tabela por mês quando o volume passar de dezenas de milhões de linhas.
- `data-track` nos botões principais para nomes legíveis no ranking (hoje o alvo é o `id` ou a tag).
- Gestão de papéis e ações sobre pedidos de exclusão no painel, com a mesma trilha de auditoria.
