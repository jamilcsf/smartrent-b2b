# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-10-04 — Página de perfil (nome, foto, senha, e-mail), pedido de exclusão de dados com análise manual e restrições temporárias; antes: calendário, bloqueio de datas, reserva e cancelamento com reembolso, SmartChat, vídeos de 1:30, fuso de Brasília e remoção do WhatsApp  

---

## 📊 Matriz de Entregas Avaliativas (Cronograma UniSENAI)

| Entrega | Foco Avaliativo | Status | Critérios Obrigatórios |
|---|---|:---:|---|
| **Etapa 1** | Pitch de Ideias e Plano de Trabalho[cite: 4] | 🟢 Concluído | Tema, ODS 8, Problema, Solução, Escopo MoSCoW[cite: 1, 4] |
| **Etapa 2** | Modelagem de Requisitos e Sistema[cite: 4] | 🟢 Concluído | RFs, RNFs, Casos de Uso, Diagramas de Atividades, DER[cite: 4] |
| **Etapa 3** | Arquitetura, Banco e CI/CD[cite: 4] | 🟡 Em Andamento | Spring Boot 3, PostgreSQL (Supabase), GitHub Actions[cite: 4] |
| **Etapa 4** | Integração Groq/Llama e Testes Mock[cite: 4] | ⚪ Pendente | `PrecificacaoIaService`, Fallback, Testes JUnit 5/Mockito[cite: 4] |
| **Etapa 5** | Frontend Chart.js e Validação em Campo[cite: 4] | ⚪ Pendente | Telas funcionais, script de 1 min, entrevista com anfitrião[cite: 4] |
| **Etapa 6** | Relatório Final e Apresentação para Banca[cite: 4] | ⚪ Pendente | PDF consolidado e slides da defesa técnica[cite: 1, 4] |

---

## 📋 Checklist de Itens Prontos vs. Próximos Passos

- [x] Definição de Tema e Contexto da Grande Florianópolis
- [x] Justificativa técnica embasada na ODS 8 (Metas 8.2, 8.3 e 8.9)[cite: 1]
- [x] Personas e Mapeamento de Dores (Anfitrião e Gestora de Pousada)
- [x] Especificação completa de Requisitos Funcionais (RF01 a RF10)
- [x] Especificação de Requisitos Não Funcionais (RNF01 a RNF08)
- [x] Diagrama de Casos de Uso e Diagramas de Atividades (Mermaid)
- [x] Modelo Conceitual e Lógico de Banco de Dados (PostgreSQL)
- [x] Script técnico para o vídeo demonstrativo de 60 segundos[cite: 4]
- [x] Inicialização do projeto Maven (Spring Boot 3, Java 17)
- [x] Implementação das entidades JPA (`Usuario`, `Imovel`, `Reserva`, `SugestaoPreco` e o embutido `Endereco`)
- [x] Repositório com query de validação de choque de datas
- [x] Geração do Modelo Físico a partir das entidades — ver [ADR-002](adr/ADR-002-estrategia-de-schema.md)
- [x] Versionamento do schema com Flyway (`V1__` e `V2__convergencia_modelo_de_dados.sql`)
- [x] Camada de Service e Controllers REST
- [x] Objetos de transferência como Records (RNF08)
- [x] Pipeline GitHub Actions (Etapa 3)
- [x] Endpoint REST de imóveis (`GET /api/imoveis`), alimentando o catálogo
- [x] Endpoints de escrita de usuário e imóvel — cadastro de usuário (cliente ou gestor) e fluxo completo de anúncios em `/api/gestor/imoveis` ([ADR-003](adr/ADR-003-ciclo-de-vida-do-anuncio.md)); o seletor de imóveis do formulário de reservas agora vem da API
- [x] Integração real com a Groq via RestClient (RNF01) para a sugestão de preço em lote (`GroqPricingSuggestionProvider`, com timeout e queda para edição manual); o `GroqApiClient` legado da rota `/api/precificacao/sugerir` continua um stub
- [x] Papel `CLIENTE`, autorização por papel e por propriedade no servidor, mídias (14 imagens somadas, 2 vídeos), máquina de estados do anúncio, jobs idempotentes e lembretes de edição esquecida
- [x] Snapshot imutável de preço e dados do imóvel em cada reserva
- [x] Autenticação (login e cadastro) com JWT e acesso público por padrão
- [x] Dashboard de estatísticas do gestor, calendário de 3 meses por imóvel e bloqueio manual de datas seguro contra concorrência ([ADR-004](adr/ADR-004-calendario-smartchat-cancelamento-video-e-fuso.md))
- [x] Reserva do cliente com gateway de pagamento **simulado**, política de cancelamento e reembolso (idempotente, com retry) e snapshot dos termos e da política
- [x] SmartChat (conversa única, criação idempotente na confirmação, SSE, filtro de conteúdo no servidor, denúncia e bloqueio como protótipo)
- [x] Vídeos de até 1:30 com envio em partes retomável e processamento assíncrono (FFmpeg com fallback); fuso de Brasília; WhatsApp removido de ponta a ponta
- [x] Página **Meu perfil** acessada pelo cabeçalho: nome de exibição, foto (PNG/JPG reprocessada, sem metadados), troca de senha (política, lista local de senhas comuns, encerramento das demais sessões) e troca de e-mail com verificação por link de uso único ([ADR-005](adr/ADR-005-perfil-seguranca-da-conta-e-exclusao-de-dados.md))
- [x] Solicitação de **exclusão de dados** com análise manual (nada é excluído automaticamente), antifraude (período mínimo, link "não fui eu", sinais de risco), restrições temporárias parciais centralizadas em `AccountRestrictionService` e trava de aprovação; serviço de revisão pronto, sem tela
- [x] Suíte de testes automatizados — 446 testes (JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest` em H2, concorrência); o front tem só verificações estáticas dos arquivos (falta teste automatizado de interface)
- [ ] Primeiro boot contra o Supabase com o schema aplicado

---

## 🔄 Estado da base de código

Em 2026-09-21 o projeto passou a usar como base a implementação desenvolvida por
Jamil Cherem, integrada a partir de `jamilcsf/smartrent-b2b` com o histórico de
autoria preservado. A base herdada trouxe camada de serviço, controladores REST,
telas estáticas, testes unitários com Mockito e esteira de integração contínua.

O trabalho em curso é adequar essa base aos requisitos desta documentação. Já
concluído:

- **RNF02** — o H2 em memória deu lugar ao PostgreSQL.
- **RNF05** — o esquema saiu de `ddl-auto=update` para migration versionada com
  Flyway, com o Hibernate apenas validando, conforme a
  [ADR-002](adr/ADR-002-estrategia-de-schema.md).
- **RNF03** — as credenciais passaram a vir de variáveis de ambiente.
- **RNF08** — a API deixou de expor entidades e passou a usar DTOs como Records.
- **RF01** — autenticação por JWT com senhas em BCrypt, mantendo o site público por
  padrão: o token identifica quem navega, sem bloquear visitantes.
- **RF04** — o catálogo deixou de ser HTML fixo e passou a vir de `GET /api/imoveis`,
  com área útil e vagas de garagem acrescentadas ao modelo pela migration V3.
- **Modelo de Dados** — as quatro entidades, o objeto de valor embutido, os enums e os
  relacionamentos agora correspondem à Seção 6.2 do Relatório Técnico.

Validado contra PostgreSQL 16.15: as duas migrations aplicam em sequência, o
Hibernate aceita o esquema em `validate`, e o cadastro de reservas funciona da
interface até o banco, com a recusa de sobreposição de datas.

Em 2026-10-03 foi entregue o **fluxo de anúncios** (ver [ADR-003](adr/ADR-003-ciclo-de-vida-do-anuncio.md)
e a seção correspondente do `README.md`): migrations V4 a V6 (status e prazos do anúncio, mídias,
rascunho de edição, histórico de preço, aceites do termo, auditoria, lembretes, notificações e snapshot
financeiro das reservas), papel `CLIENTE`, Painel do Gestor (`/dashboard.html`) com pré-publicação,
anúncios e reservas, formulário com preview e viewer 360°, e sugestão de preço por IA no portal da
transparência (`index.html`). As migrations foram validadas contra PostgreSQL 16 e o fluxo foi exercitado
de ponta a ponta contra a API em execução.

Pendente: SMTP real para o canal de e-mail dos lembretes (hoje `EMAIL_LOG`), armazenamento de mídia em
objeto (S3) para mais de uma instância, revisão jurídica do termo de uso, fluxo de reserva autoatendido por
cliente, geocodificação automática do endereço, trocar o `GroqApiClient` legado pelo novo provedor e testes
automatizados do front.

Em 2026-10-03 foi entregue também a segunda rodada (ver [ADR-004](adr/ADR-004-calendario-smartchat-cancelamento-video-e-fuso.md) e o `README.md`):
migrations V7 a V13, Dashboard (antigo Portal da transparência), calendário e bloqueio de datas, reserva do cliente com
cancelamento e reembolso, SmartChat, vídeos de 1:30 e fuso de Brasília. As migrations foram aplicadas em PostgreSQL 16 (inclusive
com dados legados de WhatsApp) e os fluxos exercitados de ponta a ponta contra a API em execução.

Pendente (depende de decisão ou de terceiros): **revisão jurídica** da política de cancelamento, dos termos e do item 5 do
termo de uso (ainda cita o WhatsApp), direito de arrependimento (`CANCEL_REGRET_DAYS`, em espera); gateway de pagamento
real; SMTP real; armazenamento de mídia em objeto (S3) com upload pré-assinado; FFmpeg em produção (em contêiner com
limites); moderação de imagens e vídeos; módulo de administração (consome denúncias e bloqueios); rate limit distribuído;
testes automatizados do front; diagrama de classes ainda sem as entidades novas.

Em 2026-10-04 foi entregue a terceira rodada (ver [ADR-005](adr/ADR-005-perfil-seguranca-da-conta-e-exclusao-de-dados.md) e o `README.md`):
migration V14 (foto, versão de sessão, auditoria de conta, troca de e-mail e solicitação de exclusão com histórico), página `/perfil.html`
e as restrições temporárias durante o pedido de exclusão. A V14 foi aplicada em PostgreSQL 16 e os fluxos (nome, foto, senha com
sessão antiga derrubada, e-mail por link, pedido e cancelamento de exclusão, restrições e aviso fixo) foram exercitados no navegador
contra a aplicação em execução.

Pendente (depende de decisão ou de terceiros), além da lista anterior: **revisão jurídica** dos textos do pedido de exclusão e dos e-mails
de segurança, do período mínimo de análise, dos prazos de retenção e do que anonimizar ou reter (`DataDeletionExecutor` é só uma interface:
**concluir a exclusão não está implementado**); SMTP real (os links de e-mail só saem em log); tela de análise e perfil de administrador;
recuperação de senha (contas criadas só pelo Google e anteriores à V14 não têm caminho para definir a primeira senha); foto em
armazenamento de objetos; moderação do conteúdo da foto; rate limit distribuído; testes automatizados do front.

## 📱 Revisão mobile (2026-10-06)

Auditoria de responsividade das 14 páginas do front (login, cadastro, catálogo, detalhe do imóvel, reserva do cliente, perfil, Dashboard,
Painel do Gestor com as três abas, formulário de anúncio, SmartChat e as telas de confirmação por link) em 320, 360, 375, 390, 414 e 768 px
(retrato) e 667 px (paisagem), com 1280 px como controle de regressão. Metodologia: Playwright com o Edge da máquina e emulação de toque,
medindo rolagem horizontal, elementos fora da tela, alvos de toque, `font-size` dos campos e erros de console/rede, mais capturas de tela de cada
página em cada largura. O backend não pôde ser usado (Docker Desktop não iniciou nesta máquina), então a API foi **simulada** por um servidor Node
de teste com dados de estresse (títulos sem espaços, e-mails e endereços longos, valores de sete dígitos); os fluxos ponta a ponta contra
Postgres real continuam pendentes de uma passada (ver abaixo).

**Antes:** rolagem horizontal em 11 das 14 páginas quando logado (cabeçalho com marca + menu + conta numa linha só, até 511 px em tela de 320 px);
título longo estourava o detalhe do imóvel (703 px); campos com 12–14 px (zoom automático no iOS); alvos de toque de 16–40 px; menu de
notificações do gestor aberto para fora da tela e sem fechar ao tocar fora; filtro do catálogo sem botão de fechar; textos de 10–11 px.

**Corrigido, com solução global:** `static/css/mobile.css` (carregado nas 12 páginas com Tailwind) + ajustes pontuais.
- Cabeçalho em duas linhas até 899 px (marca e conta em cima, menu embaixo com ícones de 44 px, `aria-label`/`aria-current` nos links do menu),
  nome do usuário truncado com reticências, `safe-area-inset-*` e `viewport-fit=cover`.
- `overflow-wrap:anywhere` global, `min-width:0` em filhos de flex/grid, imagens/canvas com `max-width:100%`, tabelas largas com rolagem
  horizontal dentro do próprio contêiner (largura mínima de 36 rem), valores monetários dos cartões do Dashboard com fonte fluida.
- Campos com `font-size:16px` (inclusive tablets em toque), alvos de toque de 44 px, caixas de seleção de 24 px, rótulos de 10–11 px elevados a 12 px,
  botões sem quebra no meio da palavra.
- Modais: rolagem do fundo travada enquanto abertos (`body:has([role=dialog])`, sem estado que possa ficar preso), altura em `dvh`, margens seguras;
  toasts com largura limitada à tela.
- Catálogo: botão **Ver resultados** e Esc no painel de filtros, rolagem do fundo travada. Painel do Gestor: o sino fecha ao tocar fora/Esc e o menu
  ocupa a largura da tela; títulos dos anúncios em duas linhas em vez de truncados. Gráficos Chart.js: tooltip por coluna (`interaction.mode:index`),
  fontes de 11–13 px e rótulos com rotação automática.

**Depois:** 0 página com rolagem horizontal nas 7 larguras e no controle de 1280 px; 0 `font-size` de campo abaixo de 16 px; 0 alvo de toque abaixo de
44 px, exceto as células do calendário (29–43 px de largura × 44 de altura em 320–414 px, acima do mínimo de 24 px do WCAG 2.2 AA; ver pendências);
0 erro de JavaScript; os 446 testes do Maven continuam passando.

**Pendências / V2:** (1) repetir a passada contra o backend real com Postgres, incluindo a ordem de cancelamento, upload de foto/vídeo e o SSE do
SmartChat (o servidor simulado não o implementa; o único erro de console restante é essa limitação); (2) teclado virtual: testado só por emulação, sem
aparelho físico (iOS Safari/Android Chrome reais); (3) células do calendário com 44 px de largura exigem layout de uma ou duas semanas por vez no
celular (V2); (4) tabelas de reservas em cartões no mobile em vez de rolagem lateral (V2); (5) Tailwind e Lucide vêm de CDN (`cdn.tailwindcss.com`
é indicado só para desenvolvimento): compilar o CSS e hospedar os ícones localmente é pendência de produção e do tempo de carregamento em 3G;
(6) o `favicon.ico` não existe (404 no console); (7) o texto `PRE_PUBLICACAO_*` aparece cru na lista "Imóveis por situação" do Dashboard
(rótulo amigável é pendência de conteúdo); (8) testes automatizados do front (Playwright) ainda não entram no pipeline.
