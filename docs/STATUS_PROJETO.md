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
**concluir a exclusão não está implementado**); SMTP real (os links de e-mail só saem em log); tela de análise e perfil de administrador;
recuperação de senha (contas criadas só pelo Google e anteriores à V14 não têm caminho para definir a primeira senha); foto em
armazenamento de objetos; moderação do conteúdo da foto; rate limit distribuído; testes automatizados do front.

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
