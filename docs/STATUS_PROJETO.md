# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-10-06 — Auditoria de segurança do SmartChat e dos uploads, com correções (filtro de contatos contra Unicode e letras espaçadas, UUID nas mensagens, 404 uniforme, cifra v2, limites de login, JWT sem segredo padrão, Spring Boot 3.5.16); antes: reforço do chat e dos uploads; cifragem em repouso (nome, foto, senha, e-mail), pedido de exclusão de dados com análise manual e restrições temporárias; antes: calendário, bloqueio de datas, reserva e cancelamento com reembolso, SmartChat, vídeos de 1:30, fuso de Brasília e remoção do WhatsApp  

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
- [x] **Cifragem em repouso** do texto do SmartChat (AES-256-GCM na aplicação, chave `SMARTCHAT_CRYPTO_KEY`, migração idempotente dos dados antigos, logs sem texto de mensagem). O servidor continua lendo o texto (filtro e denúncias); a cifragem entre os usuários (E2EE) ficou como Visão Futura V2 ([ADR-006](adr/ADR-006-cifragem-em-repouso-do-smartchat.md))
- [x] **Reforço de segurança do SmartChat e dos uploads** ([ADR-007](adr/ADR-007-reforco-de-seguranca-do-smartchat-e-dos-uploads.md)): faixa fixa de aviso; fotos de anúncio recodificadas sem EXIF/GPS (e job que reprocessa as antigas); categoria `SUSPEITA_FRAUDE` (alerta ao destinatário, sem bloquear); alertas internos de envio em massa e de fraude com limite de envio restrito e `texto_hmac`; verificação de e-mail para escrever; UUID público nas conversas (não participante recebe o mesmo 404). Auditoria das rotas com id sequencial: nenhuma permite acesso a dado de terceiros
- [x] **Auditoria de segurança do SmartChat e dos uploads** ([ADR-008](adr/ADR-008-seguranca-chat-uploads.md)): 21 achados classificados (1 crítico, 5 altos, 9 médios, 6 baixos) e corrigidos em 11 etapas — JAR com credenciais removido do repositório, `JWT_SECRET` e reCAPTCHA reais obrigatórios fora de `dev`/`test`, limites de login e cadastro, `/api/precificacao/sugerir` só para o gestor dono, filtro de contatos com NFKC/invisíveis/letras espaçadas/mensageiros isolados, UUID nas mensagens e 404 uniforme, teto de 30 MP e vídeo recusado sem FFmpeg, cifra `v2` com AAD, sanitização OWASP, Bean Validation, teto de corpo JSON, CSP básica, CORS por configuração, CI e Docker endurecidos
- [x] Suíte de testes automatizados — 671 testes (JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest` em H2, concorrência); o front tem só verificações estáticas dos arquivos (falta teste automatizado de interface)
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
**concluir a exclusão não está implementado**); SMTP real (os links de e-mail só saem em log); tela de análise de pedidos de exclusão no painel de administração (o painel de admin existe desde a ADR-009, mas só mostra contagens deles);
recuperação de senha (contas criadas só pelo Google e anteriores à V14 não têm caminho para definir a primeira senha); foto em
armazenamento de objetos; moderação do conteúdo da foto; rate limit distribuído; testes automatizados do front.

## 🛡️ Painel de administração e mapas de calor (2026-10-08)

Decisões e limites na [ADR-009](adr/ADR-009-painel-admin-e-telemetria-de-uso.md).

- **Entregue:** `/admin.html` e `/api/admin/**` só para `ADMIN` (403 para gestor/cliente, testado); visão geral, uso do site, **mapas de calor
  sobre a própria página** (por dispositivo), listas de usuários/imóveis/reservas e exportação CSV/NDJSON dos eventos. Única escrita:
  ativar/desativar conta, com auditoria e proteção contra mexer na própria conta ou em outro admin.
- **Telemetria anônima** (`eventos_uso`, migration V21; coletor `js/telemetria.js`): sem usuário, IP, texto ou cookies; respeita DNT/GPC;
  endpoint público validado e limitado; retenção de 180 dias; desligável por `TELEMETRIA_HABILITADA=false`.
- **Mudança de segurança consciente:** CSP `frame-ancestors 'self'` e `X-Frame-Options: SAMEORIGIN` (antes `'none'`/`DENY`), necessário para o iframe do mapa.
- **Pendente:** revisão jurídica (aviso/consentimento, política de privacidade, prazo de retenção definitivo); funil ordenado por sessão; rollup/partição
  da tabela quando o volume crescer; `data-track` nos botões principais; ação sobre pedidos de exclusão e gestão de papéis no painel. Os 6 PDFs
  de `docs/relatorios-fonte/` **não** foram regenerados nesta entrega (V21 e os testes CT1103–CT1123 ainda não constam neles).

## 🚨 Moderação no painel de admin (2026-10-08)

Decisões e limites na [ADR-010](adr/ADR-010-moderacao-denuncias-decisoes-automaticas-e-avisos.md).

- **Entregue:** `/moderacao.html` (só ADMIN) com denúncias (decisão com justificativa, suspensão e avisos opcionais; abrir evidência fica registrado),
  decisões automáticas do SmartChat com o efeito aplicado e revisão (descartar alerta remove a restrição de envio), contas suspensas, avisos enviados e
  histórico imutável (V22: `moderacao_acoes`, `comunicados`). Mensagens diretas a qualquer usuário em nome de um nível (administração, moderação, suporte,
  segurança), lidas em `/comunicados.html`. Suspender tira os anúncios do gestor do catálogo. O liga/desliga simples da ADR-009 foi substituído por esse fluxo.
- **A confirmar com o usuário:** "demais níveis de gerência" foi lido como o nível remetente do aviso; não há hierarquia de permissões entre admins.
- **Pendente:** textos provisórios dos avisos e decisões automatizadas (jurídico/LGPD art. 20), suspensão temporária com data de fim, resposta do usuário ao aviso,
  níveis de acesso entre admins. Os 6 PDFs de `docs/relatorios-fonte/` não foram regenerados (V21, V22 e CT1103–CT1138 ainda não constam neles).

### Qualidade e apresentação depois do painel de admin e da moderação (2026-10-08)

- **Suíte:** 720 testes, 0 falhas. **Cobertura de linhas (JaCoCo 0.8.12 como plugin avulso; não está no `pom.xml`): 85,3%** (era 84,0% na rodada de 06/10).
  Uma primeira medição, antes dos testes de serviço, deu 81,3%: `TelemetriaAnaliseService`, `AdminService` e `ComunicadoService` estavam quase sem teste automatizado (só validados à mão).
  Foram cobertos por testes de unidade, de controller e de integração com H2 e SmartChat real (`ModeracaoIntegracaoTest`). Agora: `AdminService` 99%, `TelemetriaAnaliseService` 98%,
  `ModeracaoService` 94%, `TelemetriaService` 89%, controllers novos 86% a 100%. Segue sem teste automatizado de interface.
- **Pitch (`pitch/`)**: 27 slides (eram 25). Novas demos animadas: painel de admin com mapa de calor (simulação) e moderação (da denúncia à decisão). Reescritos o slide de Dados (agora cita a telemetria
  e a exportação) e o de Big Data (a coleta existe, o processamento distribuído continua roadmap), além de contagens (18 telas, 24 controladores, 28 tabelas, migrations V1–V22), casos de teste e checklist.

## 📱 Revisão mobile (2026-10-06)

Auditoria de responsividade das 14 páginas do front (login, cadastro, catálogo, detalhe do imóvel, reserva do cliente, perfil, Dashboard,
Painel do Gestor com as três abas, formulário de anúncio, SmartChat e as telas de confirmação por link) em 320, 360, 375, 390, 414 e 768 px
(retrato) e 667 px (paisagem), com 1280 px como controle de regressão. Metodologia: Playwright com o Edge da máquina e emulação de toque,
medindo rolagem horizontal, elementos fora da tela, alvos de toque, `font-size` dos campos e erros de console/rede, mais capturas de tela de cada
página em cada largura. Na primeira passada o backend não pôde ser usado (Docker Desktop travado), então a API foi **simulada** por um servidor Node
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

**Pendências / V2:** (1) upload de foto/vídeo com o backend real (a segunda passada, abaixo, cobre reservas, calendário, IA, chat e perfil); (2) teclado virtual: testado só por emulação, sem
aparelho físico (iOS Safari/Android Chrome reais); (3) células do calendário com 44 px de largura exigem layout de uma ou duas semanas por vez no
celular (V2); (4) tabelas de reservas em cartões no mobile em vez de rolagem lateral (V2); (5) Tailwind e Lucide vêm de CDN (`cdn.tailwindcss.com`
é indicado só para desenvolvimento): compilar o CSS e hospedar os ícones localmente é pendência de produção e do tempo de carregamento em 3G;
(6) [resolvido na segunda passada] favicon; (7) o texto `PRE_PUBLICACAO_*` aparece cru na lista "Imóveis por situação" do Dashboard
(rótulo amigável é pendência de conteúdo); (8) testes automatizados do front (Playwright) ainda não entram no pipeline.

### Revisão mobile — passada com o backend real (2026-10-06)

Repetida contra a aplicação Spring Boot em execução (PostgreSQL 16 no Docker, banco próprio `smartrent_mobile`, `scripts/dados-demonstracao.sql`
mais títulos longos e dois anúncios em pré-publicação). Auditoria de 18 telas em 320, 360, 375, 390, 414, 768, 667 paisagem e 1280 px, e fluxos
com toque em 375 e 320 px (20 verificações, todas aprovadas):
- Reserva do cliente: datas invertidas recusadas; período em conflito avisado já na prévia e recusado ao confirmar; modal de confirmação dentro da tela,
  com rolagem do fundo travada e liberada ao fechar; cancelamento abre o modal.
- Reserva do gestor: "check-out deve ser posterior à de check-in" e "Conflito de datas detectado para este imovel." exibidos; calendário abre o
  popover da reserva ao toque, dentro da tela.
- Sugestão de preço por IA em estado de contingência (sem chave configurada): a tabela mostra "A sugestão por IA não está configurada neste ambiente",
  sem rolagem horizontal, e o valor pode ser definido à mão.
- Sino de notificações, SmartChat (SSE real, sem erro de console) e perfil sem rolagem horizontal.

Dois defeitos novos achados e corrigidos nesta passada: as abas do Painel do Gestor se sobrepunham em 320 px (`[role=tablist]` agora não encolhe as abas) e a
tira de abas do formulário de anúncio passou a rolar em vez de estourar a página; o `favicon.ico` inexistente (404) ganhou `img/favicon.svg`.
Resultado final: 0 rolagem horizontal, 0 campo abaixo de 16 px, 0 alvo de toque abaixo de 44 px (exceto células do calendário e links dentro de texto),
0 erro de JavaScript. Erros de console remanescentes são externos ao front: o botão do Google recusa a origem `localhost` (403) e o link de confirmação
de e-mail de teste devolve 400 por token inválido.

Ainda pendente: envio do formulário de login/cadastro (exige resolver o reCAPTCHA, que a automação não faz), caminho feliz da sugestão de preço da IA
(sem chave Groq configurada), teclado virtual e rolagem em aparelhos reais (iOS Safari/Android Chrome).

Em 2026-10-05 foi entregue a cifragem em repouso do SmartChat (ver [ADR-006](adr/ADR-006-cifragem-em-repouso-do-smartchat.md) e o `README.md`):
migration V15 (as três colunas de texto viram `TEXT`), `CifraCampo` (AES-256-GCM, `v1:` + Base64), conversor JPA, falha na inicialização
sem `SMARTCHAT_CRYPTO_KEY` fora de `dev`/`test` e job idempotente que cifra o legado. A V15 e a migração foram exercitadas em PostgreSQL 16
com `ddl-auto=validate`.

Pendente (decisão do jurídico ou de infraestrutura): **prazo de retenção de `texto_original`**; **tratamento das mensagens na exclusão de
conta** (ADR-005, executor ainda sem implementação); cofre e backup
da chave (perdê-la torna as mensagens irrecuperáveis) e rotina de rotação; E2EE como V2.

Em 2026-10-05 foi entregue também o reforço de segurança do SmartChat e dos uploads (ver [ADR-007](adr/ADR-007-reforco-de-seguranca-do-smartchat-e-dos-uploads.md) e o `README.md`):
migrations V16 a V19 (`metadados_removidos` nas mídias, `texto_hmac` e `alertas_internos`, `email_verificado_em` e `verificacao_email`, `codigo_publico` das conversas),
`ImagemSegura`/`SaneamentoFotosAnuncio`, `AnaliseComportamentoChat`, `VerificacaoEmailService` e as rotas `/api/smartchat/conversas/{codigo}`. As migrations V15 a V19 foram aplicadas em
PostgreSQL 16 e o fluxo foi exercitado contra a API em execução.

Pendente: revisão jurídica dos textos novos (aviso fixo, alerta de fraude, e-mail de verificação); SMTP real (o link de verificação só sai em log);
retenção de `texto_hmac` e dos alertas internos; metadados no vídeo do modo básico (sem FFmpeg); uniformizar 403/404 nas rotas de reserva e notificação;
autenticar `GET /api/precificacao/sugerir` (público e grava). **V2 (não implementado):** imagens e comprovantes no chat, verificação por telefone e por documento
(e o selo de identidade verificada), classificação de fraude por IA.

Em 2026-10-06 o serviço foi publicado no Render (Docker) com o banco no Supabase (migrations V1 a V19 aplicadas). Foi acrescentado o envio de e-mail de verdade pela API HTTPS da Brevo
(`BREVO_API_KEY` e `EMAIL_REMETENTE`; o Render gratuito bloqueia SMTP), que substitui o log quando a chave existe e nunca registra corpo nem endereços; e foi removida a senha padrão que o Spring Boot
imprimia no log. Pendente: configurar a conta da Brevo e o reCAPTCHA real no ambiente; o e-mail de notificações do chat (`NotificadorEmailLog`) continua só em log.

Em 2026-10-06 foi concluída a auditoria de segurança do SmartChat e dos uploads (ver [ADR-008](adr/ADR-008-seguranca-chat-uploads.md), o `README.md` e o `.env.example`):
migration V20 (`codigo_publico` das mensagens), `AcessoOcultoException`, `LimitesDeAutenticacao`, `JwtConfig`, cifra `v2` com um conversor por coluna, `LimiteDeCorpoFilter`,
`VideoProcessingIndisponivel`, Spring Boot 3.5.16 com Flyway 11. A suíte passa com 671 testes; a cobertura de linhas medida com o JaCoCo é de 84,0% no total, e os componentes novos ficam
entre 86% e 100% (`JwtConfig` 86%, `LimitesDeAutenticacao` 100%, `Sanitizador` 100%, `LimiteDeCorpoFilter` 100%, `CifraCampo` 93%, `MessageFilterService` 96%); `ChatEventos` (65%) e `VideoConfig`
(61%) têm trechos de borda sem teste.

**Atenção antes do próximo deploy no Render:** (1) **rotacionar a senha do banco no Supabase**: ela estava no JAR versionado e continua no histórico do git (a reescrita do histórico não foi feita; o roteiro está na ADR-008);
(2) definir `RECAPTCHA_SITE_KEY` e `RECAPTCHA_SECRET` reais, sem os quais a aplicação **não sobe**; (3) conferir `JWT_SECRET`, `SMARTCHAT_CRYPTO_KEY` e `SMARTCHAT_HMAC_KEY` (e o backup da chave de cifra);
(4) a V20 e o Flyway 11 foram validados só em H2: subir uma vez contra o PostgreSQL antes de publicar.

Resolvido nesta rodada (antes pendente): 403/404 uniforme em reserva, notificação e imóvel; `GET /api/precificacao/sugerir` autenticado; vídeo sem FFmpeg (agora recusado fora de `dev`/`test`).
Pendente: validar V20 e Flyway 11 em PostgreSQL real; rotacionar a credencial e, com autorização, limpar o histórico; reserva, notificação e imóvel ainda com id numérico nas rotas.
**V2 (não implementado):** negar por padrão no `SecurityConfig`, UUID em reserva/notificação/imóvel, CSP com nonce, moderação por IA e detecção semântica de burla do filtro, imagens e comprovantes no chat,
rotação de chave da cifra e AAD com id da linha, rate limit distribuído, scan de CVE com banco de vulnerabilidades no CI.
