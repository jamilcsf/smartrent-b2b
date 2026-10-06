# Projeto_Aplicado_IV

# 🏖️ Sistema B2B de Gestão Inteligente para Aluguel por Temporada

Plataforma web B2B para anfitriões independentes, pousadas de pequeno porte e gestores de imóveis (*property managers*) da Grande Florianópolis, com sugestão de preços diários via Inteligência Artificial baseada em sazonalidade e localização.

> Projeto Aplicado IV — Curso de Análise e Desenvolvimento de Sistemas (ADS), UniSENAI.

---

## 📌 Sobre o Projeto

Anfitriões e gestores de imóveis frequentemente definem o preço da diária de forma subjetiva, sem considerar de maneira estruturada a sazonalidade, o dia da semana e a localização do imóvel. Este sistema centraliza o cadastro de imóveis e reservas e utiliza uma API de IA (Groq/OpenAI) para sugerir preços competitivos, reduzindo a subjetividade da precificação e o tempo gasto em tarefas operacionais.

## ✨ Funcionalidades

- 🔐 Autenticação de usuários (anfitrião/gestor), reaproveitada e evoluída do Projeto Aplicado III
- 🏠 Cadastro e gestão completa de imóveis (CRUD)
- 📅 Cadastro e gestão de reservas, com validação de conflito de datas
- 🤖 Sugestão de preço diário via IA, contextualizada por bairro, data e sazonalidade
- 📊 Visualização gráfica da evolução dos preços sugeridos (Chart.js)
- 🗂️ Histórico de sugestões de preço geradas

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Backend | Java 17+, Spring Boot 3.x (Spring Web, Spring Data JPA) |
| Integração com IA | `RestClient` nativo, consumindo API Groq/OpenAI |
| Banco de Dados | PostgreSQL (hospedado no Supabase) |
| Frontend | HTML5, CSS3 responsivo (mobile-first), JavaScript Vanilla, Chart.js |
| Testes | JUnit 5, Mockito |
| CI/CD | GitHub Actions |
| Deploy | Render (backend) + Vercel (frontend) + Supabase (banco) |

## 📁 Estrutura do Projeto

```
src/main/java/com/temporada/gestao
├── controller     # Camada de entrada das requisições (REST)
├── service        # Regras de negócio, incluindo PrecificacaoIaService
├── repository     # Acesso a dados via Spring Data JPA
├── model          # Entidades: Usuario, Imovel, Reserva, SugestaoPreco
├── dto            # Objetos de transferência de dados (records)
└── config         # Configurações, incluindo RestClientConfig
```

## ✅ Pré-requisitos

- Java 17 ou superior
- Maven 3.8+
- Conta no [Supabase](https://supabase.com) (ou PostgreSQL local para desenvolvimento)
- Chave de API da [Groq](https://console.groq.com) ou OpenAI

## ⚙️ Configuração e Instalação

1. Clone o repositório:
   ```bash
   git clone https://github.com/<sua-org>/gestao-aluguel-temporada.git
   cd gestao-aluguel-temporada
   ```

2. Configure as variáveis de ambiente (nunca versionar credenciais no repositório):
   ```bash
   export DATABASE_URL=jdbc:postgresql://<host-supabase>:5432/postgres
   export DATABASE_USERNAME=<usuario>
   export DATABASE_PASSWORD=<senha>
   export IA_API_KEY=<sua-chave-groq-ou-openai>
   export IA_API_URL=https://api.groq.com/openai/v1/chat/completions
   export IA_API_MODEL=llama-3.3-70b-versatile
   export SMARTCHAT_CRYPTO_KEY=<base64-de-32-bytes>   # ver "Cifragem em repouso do SmartChat"
   export SMARTCHAT_HMAC_KEY=<base64-de-pelo-menos-32-bytes>   # openssl rand -base64 32
   ```

3. Compile e execute os testes:
   ```bash
   mvn clean test
   ```

4. Inicie o backend localmente:
   ```bash
   mvn spring-boot:run
   ```
   A API ficará disponível em `http://localhost:8080`.

5. Abra o frontend (`/frontend`) em um navegador ou sirva com uma extensão como Live Server, apontando as chamadas `fetch()` para a URL do backend.

## 🔌 Principais Endpoints da API

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/auth/registrar` | Cadastra um novo usuário |
| `POST` | `/api/auth/login` | Autentica um usuário |
| `GET` \| `POST` | `/api/imoveis` | Lista ou cadastra imóveis |
| `PUT` \| `DELETE` | `/api/imoveis/{id}` | Atualiza ou remove um imóvel |
| `GET` \| `POST` | `/api/imoveis/{id}/reservas` | Lista ou cadastra reservas de um imóvel |
| `GET` | `/api/imoveis/{id}/sugestao-preco?data=YYYY-MM-DD` | Retorna a sugestão de preço da IA para a data informada |

## 🏠 Fluxo de anúncios de imóveis

Perfis: **cliente** (`CLIENTE`) só navega pelo catálogo; **gestor** (`ANFITRIAO`, ou `ADMIN`) acessa o
**Painel do Gestor** (`/dashboard.html`) e só vê e altera os **próprios** imóveis. A autorização é validada no
backend (`/api/gestor/**` e `/api/reservas/**` respondem 403 a quem não é gestor; imóvel alheio também é 403).
O cadastro (`/cadastro.html`) deixa escolher "Quero alugar" ou "Sou gestor de imóveis".

Ciclo de vida (`StatusAnuncio`, transições validadas em `MaquinaDeEstados`):

```
PRE_PUBLICACAO_SEM_PRECO ─preço─▶ PRE_PUBLICACAO_AGUARDANDO ─24h─▶ PRONTO_PARA_PUBLICAR ─publicar─▶ PUBLICADO
                                  (relógio NÃO reinicia)                                              │  ▲
                                                                          editar (sai do ar)          ▼  │ descartar (volta na hora)
                                                             REPUBLICACAO_AGENDADA ◀─confirmar─ EM_EDICAO
                                                              (volta sozinho em 2h)     (sem prazo máximo)
```

| Método | Endpoint (gestor) | Descrição |
|---|---|---|
| `POST` \| `GET` | `/api/gestor/imoveis` | Cria anúncio (com aceite do termo) / lista os meus |
| `GET` \| `PUT` | `/api/gestor/imoveis/{id}` | Detalhe / edição direta (só em pré-publicação) |
| `POST` | `/api/gestor/imoveis/{id}/midias` | Upload de imagem (`arquivo`, `tipo` = `FOTO` \| `FOTO_360`) ou vídeo pequeno; 14 imagens somadas, 2 vídeos de até **1:30** |
| `DELETE` \| `PUT` | `/api/gestor/imoveis/{id}/midias/{mid}` · `/midias/ordem` · `/midias/{mid}/capa` · `/midias/{mid}/tipo` | Remover, reordenar, capa, comum ↔ 360 |
| `PUT` | `/api/gestor/imoveis/{id}/preco` | Define/altera o preço em pré-publicação (`origem` MANUAL ou IA) |
| `POST` | `/api/gestor/imoveis/{id}/publicar` | Publica (exige 24h da 1ª confirmação de preço e novo aceite) |
| `POST` \| `PUT` | `/api/gestor/imoveis/{id}/edicao/iniciar` · `/edicao/rascunho` | Tira do ar e abre rascunho / salva rascunho |
| `POST` | `/api/gestor/imoveis/{id}/edicao/confirmar` · `/edicao/descartar` | Aplica (volta em 2h) / descarta (volta na hora) |
| `POST` | `/api/gestor/precificacao/sugestoes` | Sugestão de preço por IA em lote (só sugere) |
| `GET` \| `POST` | `/api/gestor/notificacoes` · `/{id}/lida` | Sino do painel |
| `GET` | `/api/termos/atual` · `/api/midias/{chave}` | Termo de uso · arquivo da mídia (chave aleatória) |

O catálogo público (`/api/imoveis`) só devolve anúncios **visíveis**: `PUBLICADO`, ou `REPUBLICACAO_AGENDADA` cujo
horário já chegou (assim um atraso do job não prolonga a suspensão). O contato com o gestor é só pelo SmartChat (o WhatsApp foi removido).
Reservas gravam um **snapshot imutável** de preço e dados do imóvel; mudar o preço depois não as afeta.

Variáveis de ambiente (valores padrão entre parênteses):

| Variável | Efeito |
|---|---|
| `SMARTRENT_JANELA_PRE_PUBLICACAO_HORAS` (24) · `SMARTRENT_REPUBLICACAO_HORAS` (2) | Janela de pré-publicação e suspensão após confirmar edição |
| `SMARTRENT_LEMBRETE_APOS_HORAS` (24) · `SMARTRENT_LEMBRETE_INTERVALO_HORAS` (24) · `SMARTRENT_LEMBRETE_MAX` (5) | Lembrete de edição esquecida |
| `SMARTRENT_JOBS_HABILITADO` (true) | Liga/desliga os jobs agendados |
| `MIDIA_DIRETORIO` (`./dados/midias`) · `MIDIA_TAMANHO_MAX_IMAGEM_MB` (10) | Armazenamento e limite de imagem |
| `GROQ_API_KEY` · `GROQ_MODEL` · `GROQ_BASE_URL` · `GROQ_TIMEOUT_MS` | IA de precificação (sem chave, cai para edição manual) |
| `FORWARD_HEADERS_STRATEGY` (none) | `framework` atrás de proxy, para registrar o IP real do aceite |

Para testar o fluxo sem esperar, rode com `SMARTRENT_JANELA_PRE_PUBLICACAO_HORAS=0` e
`SMARTRENT_REPUBLICACAO_HORAS=0` (ou ajuste os timestamps no banco). Decisões e suposições: [ADR-003](docs/adr/ADR-003-ciclo-de-vida-do-anuncio.md).

## 📅 Painel do Gestor, calendário, reservas e cancelamento

O antigo "Portal da transparência" agora é o **Dashboard** (`/estatisticas.html`: só estatísticas dos imóveis do gestor;
a rota antiga redireciona). O **Painel do Gestor** (`/dashboard.html`) concentra "Criar anúncio", pré-publicação,
sugestão de preço por IA, anúncios e a aba **Reservas e calendário**: três meses lado a lado do imóvel escolhido (cinza =
confirmada, hachurado = pendente, vermelho com listras e cadeado = bloqueado, dia de troca dividido na diagonal),
popover acessível com hóspede, e-mail, datas e "Abrir conversa no SmartChat", e bloqueio de datas (clique inicial e final).

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/gestor/calendario?imovelId&de&ate` | Reservas e bloqueios de **um** imóvel (dados do hóspede só para o dono) |
| `GET` \| `POST` | `/api/gestor/imoveis/{id}/bloqueios` | Lista / cria bloqueio (`apenasLivres`, `cancelarPendentes` para as confirmações) |
| `DELETE` | `/api/gestor/imoveis/{id}/bloqueios/{bid}?de&ate` | Remove o bloqueio inteiro ou só uma parte (divide em dois) |
| `GET` | `/api/gestor/estatisticas?meses=` | Dashboard do gestor autenticado |
| `POST` | `/api/reservas/{id}/cancelar` | Cancelamento pelo gestor (motivo obrigatório; reembolso integral) |
| `GET` | `/api/cliente/reservas/previa?imovelId&dataCheckin&dataCheckout&numeroHospedes` | Valores, regras e política antes de reservar |
| `POST` \| `GET` | `/api/cliente/reservas` · `/{id}` | Cria reserva pendente (exige ciência se nascer sem reembolso) / lista e detalha as minhas |
| `POST` | `/api/cliente/reservas/{id}/pagar` | Paga (gateway **simulado**, idempotente) e confirma |
| `GET` \| `POST` | `/api/cliente/reservas/{id}/simulacao-cancelamento` · `/cancelar` | Simulação calculada no servidor / cancelamento |
| `GET` | `/api/politica-cancelamento` · `/api/imoveis/{id}/indisponibilidade` · `/api/config/agora` | Política (texto provisório) · datas indisponíveis (sem revelar bloqueio) · "hoje" em Brasília |

Cancelamento: reembolso integral até **48 h antes do horário de check-in** (14:00, Brasília), sem reembolso depois,
integral quando o gestor cancela, sem cobrança para reserva pendente; estorno idempotente com retry. Os parâmetros da
política ficam gravados na reserva. Os textos da política são **provisórios e pendentes de revisão jurídica**
(`src/main/resources/textos-pendentes-juridico.properties`); `CANCEL_REGRET_DAYS` (arrependimento) fica `0` e sem efeito.

## 💬 SmartChat

Canal único entre cliente e gestor (`/smartchat.html`). O cliente abre pela página do anúncio (só publicado), pela
reserva ou automaticamente quando a reserva é confirmada; a aba "SmartChat" aparece para ele depois da primeira
interação (flag no backend) e sempre para o gestor. Telefones, e-mails, links externos e conteúdo impróprio são
**borrados no servidor** (o original nunca chega ao navegador); links de `SMARTCHAT_DOMINIOS_PERMITIDOS` passam.
Denúncia e bloqueio de usuário são **protótipo**: persistem, sem efeito (`SMARTCHAT_BLOCK_ENFORCEMENT=false`).

Reforço de segurança ([ADR-007](docs/adr/ADR-007-reforco-de-seguranca-do-smartchat-e-dos-uploads.md)): faixa fixa de aviso no topo das mensagens; mensagens que citam pagamento ou contato
fora da plataforma (`SUSPEITA_FRAUDE`) **não são bloqueadas**, mas o destinatário vê um alerta e a equipe recebe um alerta interno; o mesmo
texto enviado a várias conversas em pouco tempo gera um alerta interno e restringe o limite de envio (sem suspender a conta); só quem tem
**e-mail verificado** envia mensagem ou inicia conversa (ler continua livre); conversas usam **UUID** nas rotas, nos eventos e nos links.
Fotos de anúncio são recodificadas **sem EXIF/GPS** no envio e as já armazenadas são reprocessadas uma vez no boot (`MIDIA_SANEAR_AO_INICIAR`).

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/smartchat/conversas?imovelId=` · `/nao-lidas` | Minhas conversas / contador |
| `POST` | `/api/smartchat/conversas/por-imovel/{imovelId}` · `/por-reserva/{reservaId}` | Abre ou reutiliza a conversa (conversa **nova** exige e-mail verificado) |
| `GET` | `/api/smartchat/conversas/{codigo}` | Uma conversa; `codigo` é o **UUID** público (o id sequencial não sai da API) |
| `GET` \| `POST` | `/api/smartchat/conversas/{codigo}/mensagens` | Lê (texto já filtrado) / envia (exige e-mail verificado) |
| `POST` | `/api/smartchat/conversas/{codigo}/lidas` · `/denuncias` · `/bloqueio` | Leitura, denúncia, pedido de bloqueio |
| `GET` | `/api/smartchat/conversas/{codigo}/perfil` | Perfil (sem e-mail nem telefone; selo "E-mail verificado") |
| `POST` | `/api/perfil/email/verificacao/reenviar` | Reenvia o link de verificação do e-mail (com limite de taxa) |
| `POST` | `/api/perfil/email/verificar` | Confirma o link (público; o token de uso único é a prova) |
| `POST` \| `GET` | `/api/smartchat/stream-ticket` · `/stream?ticket=` | Tempo quase real por SSE (ticket de uso único) |

## 🎬 Vídeos

Até 2 vídeos de **1:30** por anúncio, fora das 14 imagens. O envio é em partes retomáveis
(`POST/PUT/DELETE /api/gestor/imoveis/{id}/videos/uploads`), a duração é validada no servidor e o processamento é
assíncrono (`ENVIANDO → PROCESSANDO → PRONTO | FALHA`). Com **FFmpeg** (`VIDEO_FFMPEG_PATH` ou no `PATH`) gera H.264+AAC
faststart, HLS 360/720/1080, poster e remove metadados; sem ele usa o processamento básico (só valida e publica o
MP4). Publicar e confirmar alteração exigem todos os vídeos prontos.

## 🕒 Fuso horário e variáveis novas

Tudo que depende de dia ou hora segue **America/Sao_Paulo** (`APP_TIMEZONE`): instantes novos em UTC, datas de calendário
sem fuso, prazos (24h, 2h, lembretes) como tempo decorrido.

| Variável | Efeito |
|---|---|
| `APP_TIMEZONE` (`America/Sao_Paulo`) | Fuso oficial da plataforma |
| `CANCEL_ANTECEDENCIA_HORAS` (48) · `CHECKIN_HORA_PADRAO` (14:00) · `CANCEL_REGRET_DAYS` (0) · `RESERVA_PENDENTE_EXPIRA_MINUTOS` (30) | Política de cancelamento e expiração de pendente |
| `SMARTCHAT_CRYPTO_KEY` · `SMARTCHAT_CRYPTO_MIGRATE_ON_START` (true) | Chave AES-256 (Base64 de 32 bytes) da cifra em repouso e migração das linhas antigas; ver abaixo |
| `SMARTCHAT_HMAC_KEY` | Chave (Base64, ≥ 32 bytes; `openssl rand -base64 32`) do HMAC que compara textos iguais sem guardá-los. **Obrigatória fora de `dev`/`test`**: sem ela a aplicação não sobe |
| `SMARTCHAT_ENVIO_MASSA_CONVERSAS` (5) · `SMARTCHAT_ENVIO_MASSA_CONVERSAS_CONTA_NOVA` (3) · `SMARTCHAT_ENVIO_MASSA_MINUTOS` (10) · `SMARTCHAT_CONTA_NOVA_DIAS` (7) | Envio em massa: N conversas distintas com o mesmo texto em M minutos; conta com menos de X dias tem limiar menor |
| `SMARTCHAT_LIMITE_RESTRITO_POR_MINUTO` (3) · `SMARTCHAT_LIMITE_RESTRITO_MINUTOS` (60) · `SMARTCHAT_ALERTA_JANELA_MINUTOS` (60) | Limite de envio após o alerta, por quanto tempo vale e janela sem repetir o mesmo alerta |
| `PERFIL_EMAIL_VERIFICACAO_HORAS` (24) · `MIDIA_SANEAR_AO_INICIAR` (true) | Validade do link de verificação de e-mail; reprocessa as fotos de anúncio antigas sem EXIF/GPS |
| `SMARTCHAT_DOMINIOS_PERMITIDOS` · `SMARTCHAT_TERMOS_ARQUIVO` · `SMARTCHAT_BLOCK_ENFORCEMENT` (false) · `SMARTCHAT_MENSAGENS_POR_MINUTO` (20) | Filtro de conteúdo, termos e limites do chat |
| `VIDEO_FFMPEG_PATH` · `VIDEO_DURACAO_MAX_SEGUNDOS` (90) · `VIDEO_TAMANHO_MAX_MB` (500) · `VIDEO_APAGAR_ORIGINAL` (true) | Processamento e limites de vídeo |

Contas de demonstração (`scripts/dados-demonstracao.sql`, senha `senhaSegura123`): `ana@smartrent.dev` (gestora) e
`cliente@smartrent.dev` (cliente). Decisões, suposições e pendências jurídicas: [ADR-004](docs/adr/ADR-004-calendario-smartchat-cancelamento-video-e-fuso.md).

### 🔐 Cifragem em repouso do SmartChat

O texto das mensagens (`texto_filtrado`, `texto_original`), a descrição das denúncias e a prévia das notificações são gravados **cifrados** com
AES-256-GCM pela aplicação (formato `v1:` + Base64). Isto protege contra vazamento do banco, de backups e contra acesso
direto ao Supabase. **Não é cifragem entre os usuários**: o servidor continua lendo o texto, pois o filtro de conteúdo e
as denúncias dependem disso, e quem tiver a chave e o banco ao mesmo tempo lê tudo. Detalhes: [ADR-006](docs/adr/ADR-006-cifragem-em-repouso-do-smartchat.md).

- **Gerar a chave** (32 bytes em Base64) e exportá-la como variável de ambiente:
  ```bash
  openssl rand -base64 32
  export SMARTCHAT_CRYPTO_KEY="<saida-do-comando>"
  ```
- **Fora dos perfis `dev` e `test` a aplicação não sobe** sem a chave, ou com chave que não decodifica para 32 bytes.
  Para desenvolvimento local, `SPRING_PROFILES_ACTIVE=dev` (o `executar.bat` já define) usa uma chave pública de
  desenvolvimento, que **não protege nada**.
- ⚠️ **Perder a chave torna as mensagens irrecuperáveis.** Guarde-a em um cofre de segredos, com backup separado do banco.
  Não a coloque no repositório nem em log, e não a troque sem um plano de rotação (a versão `v1` do formato existe para isso).
- Ao subir, a aplicação cifra as linhas antigas em texto puro (idempotente, só registra contagens). Depois de concluída,
  pode desligar com `SMARTCHAT_CRYPTO_MIGRATE_ON_START=false`.
- Não filtre nem ordene por essas colunas em queries: o banco só enxerga texto cifrado.
- **Trânsito:** em produção, `DATABASE_URL` deve exigir TLS, por exemplo
  `jdbc:postgresql://<host-supabase>:5432/postgres?sslmode=require`.
- Os logs do chat não registram o texto das mensagens.

## 👤 Meu perfil, segurança da conta e exclusão de dados

Avatar + nome no cabeçalho de todas as páginas levam a `/perfil.html` (login obrigatório; o usuário só vê e altera o
**próprio** perfil — nenhum endpoint recebe id de usuário). Seções: dados da conta (foto, nome de exibição, e-mail), segurança
(senha) e privacidade (pedido de exclusão de dados). Decisões e pendências jurídicas: [ADR-005](docs/adr/ADR-005-perfil-seguranca-da-conta-e-exclusao-de-dados.md).

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` · `PATCH` | `/api/perfil` | Lê o perfil (com pedido de exclusão e restrições ativas) · altera o nome de exibição |
| `POST` · `DELETE` | `/api/perfil/foto` | Envia a foto (PNG/JPG, reprocessada no servidor) · remove (volta às iniciais) |
| `GET` | `/api/perfil/foto/{arquivo}` | Foto pública (nome aleatório); é o que aparece a outros usuários no SmartChat |
| `POST` | `/api/perfil/senha` | Troca a senha (exige a atual); derruba as outras sessões e devolve token novo |
| `POST` | `/api/perfil/email` | Pede a troca do e-mail (exige a senha atual; resposta sempre neutra) |
| `POST` | `/api/perfil/email/confirmar` | Confirma o link recebido no novo e-mail (público; token de uso único) |
| `GET` · `POST` · `DELETE` | `/api/perfil/exclusao-dados` | Situação e textos · pede a exclusão (exige a senha atual) · cancela o pedido |
| `POST` | `/api/perfil/exclusao-dados/cancelar` | "Não fui eu": cancela pelo link do e-mail (público; token de uso único) |

**Nada é excluído automaticamente.** O pedido entra em análise da equipe (sem tela ainda: `DataDeletionReviewService`, sem endpoint)
e, enquanto está em andamento, a conta sofre restrições temporárias parciais: o cliente não cria reservas novas, os anúncios do gestor
não aceitam novas reservas, e o gestor não publica, não republica nem confirma alteração; ninguém altera o e-mail. Login, leitura,
troca de senha, SmartChat, reservas confirmadas, cancelamentos e reembolsos, descarte de edição e bloqueio de datas **não** são restritos.

| Variável | Efeito |
|---|---|
| `PERFIL_FOTO_MAX_MB` (2) · `PERFIL_FOTO_MIN_LADO` (128) · `PERFIL_FOTO_MAX_PIXELS` (25000000) · `PERFIL_FOTO_LADO` (512) | Limites e tamanho final da foto |
| `PERFIL_SENHA_MINIMA` (10) · `PERFIL_SENHAS_COMUNS_ARQUIVO` | Política da nova senha e lista local de senhas comuns |
| `PERFIL_SENHA_TENTATIVAS` (5) · `PERFIL_SENHA_JANELA_MINUTOS` (15) · `PERFIL_EMAIL_PEDIDOS_POR_HORA` (3) · `PERFIL_FOTO_UPLOADS_POR_HORA` (10) | Limites de tentativas |
| `PERFIL_EMAIL_TOKEN_MINUTOS` (60) · `APP_BASE_URL` | Validade e endereço dos links enviados por e-mail |
| `BREVO_API_KEY` · `EMAIL_REMETENTE` · `EMAIL_REMETENTE_NOME` (SmartRent) | E-mail de verdade pela API HTTPS da Brevo (o Render gratuito bloqueia SMTP). Sem a chave, os e-mails ficam só em log. `EMAIL_REMETENTE` deve ser um endereço verificado na conta |
| `R2_BUCKET` · `R2_ENDPOINT` · `R2_ACCESS_KEY_ID` · `R2_SECRET_ACCESS_KEY` · `R2_URL_PUBLICA` | Fotos (anúncio, miniaturas, perfil) no Cloudflare R2. Só ligam com bucket e endpoint (`https://<conta>.r2.cloudflarestorage.com`); `R2_URL_PUBLICA` (domínio do bucket) faz o site redirecionar para a CDN. Vídeos continuam no disco |
| `EMAIL_LOG_CORPO` (true) | Enquanto o e-mail é só log: `false` em produção, para links e tokens não irem ao log |
| `DATA_DELETION_MIN_REVIEW_HOURS` (48) · `DATA_DELETION_RISK_DAYS` (30) · `DATA_DELETION_NOTIFY_EMAIL` · `DATA_DELETION_RESTRICTIONS_ENABLED` (true) · `DATA_DELETION_REQUESTS_PER_DAY` (3) | Pedido de exclusão: análise mínima, sinais de risco, aviso à equipe e restrições |

Como testar à mão: entre como `cliente@smartrent.dev`, clique no nome no cabeçalho e experimente nome, foto, senha (a de demonstração, `senhaSegura123`,
está na lista de senhas comuns: ela vale como senha **atual**, mas não como nova), e-mail (o link aparece no log do servidor, `[e-mail simulado]`) e
"Solicitar exclusão de dados" (o link "Não fui eu" também sai no log). Textos de e-mail, modal e avisos são **provisórios** e ficam em
`src/main/resources/textos-pendentes-juridico.properties`, pendentes de revisão do setor responsável.

## 🧪 Testes

O projeto utiliza JUnit 5 e Mockito, isolando a chamada externa à API de IA para evitar dependência de rede e custos desnecessários durante a suíte de testes:

```bash
./mvnw test
```

## 🚀 CI/CD e Deploy

A cada `push` na branch `main`, o GitHub Actions executa build e testes automaticamente (`.github/workflows/ci.yml`). O deploy contínuo ocorre em:

- **Backend:** Render, com variáveis de ambiente configuradas no painel do serviço
- **Frontend:** Vercel, com deploy automático a partir do repositório
- **Banco de Dados:** Supabase (PostgreSQL gerenciado)

### 🧭 Render (online) e ambiente local

O site no Render e o `localhost` são independentes: o Render roda o código da `main` do GitHub, então mexer nos arquivos locais não o altera até haver `push` e `merge`.

- **Banco:** o Render usa o Supabase. No ambiente local use o PostgreSQL do Docker (porta 55432); nunca aponte `DATABASE_URL` do terminal ou do `.env` para o Supabase, ou os testes alteram os dados da demonstração.
- **Chaves:** não copie `SMARTCHAT_CRYPTO_KEY`/`SMARTCHAT_HMAC_KEY` do Render para o ambiente local, que usa a chave pública de desenvolvimento. Mensagens cifradas com uma chave não abrem com a outra.
- **Segredos:** nunca versione arquivos com `BREVO_API_KEY`, `RECAPTCHA_SECRET`, `R2_*`, senhas do banco ou chaves de cifragem.
- **Fotos no Render:** sem as variáveis `R2_*`, as fotos ficam no disco do Render, apagado a cada deploy ou reinício. Cadastre os anúncios com fotos pouco antes de usar o site.
- **Deploys:** com o auto-deploy ligado, todo `push` na `main` refaz o deploy e apaga essas fotos. Antes de uma demonstração, adie o `merge` ou desligue o auto-deploy (Settings → Build & Deploy).
- **Hibernação:** o plano gratuito hiberna o serviço; a primeira visita leva cerca de 3 minutos. Abra o site uns 5 minutos antes de apresentá-lo.

## 🗺️ Roadmap (V2)

- Aplicativo nativo em Flutter
- Integração via iCal com OTAs (Airbnb/Booking)
- Módulo de cobrança/assinatura SaaS
- Histórico analítico comparando sugestões da IA com preços efetivamente praticados

## 👥 Equipe

Projeto desenvolvido por Arthur Moreira, Douglas do Carmo e Jamil Cherem como Projeto
Aplicado IV do curso de Análise e Desenvolvimento de Sistemas — UniSENAI.
Orientação: Profa. Milena Maredmi Correa.

## 📄 Licença

Projeto acadêmico desenvolvido para fins avaliativos da disciplina Projeto Aplicado IV.

## ▶️ Como executar

### Windows: um clique

```
executar.bat
```

O script confere os pré-requisitos, sobe o PostgreSQL, compila, inicia a
aplicação, insere os dados de demonstração e abre o navegador. Para encerrar
tudo e descartar o banco, use `parar.bat`.

Pré-requisitos: **JDK 17 ou superior** e **Docker Desktop** em execução. Maven
não é necessário — o projeto traz o Maven Wrapper.

Login de demonstração: `ana@smartrent.dev` / `senhaSegura123`.

### Passo a passo manual


### Pré-requisitos

- **JDK 17** ou superior, com `JAVA_HOME` apontando para ele
- **PostgreSQL** acessível (um container descartável resolve)
- Maven não é necessário se você usar o wrapper do seu ambiente

### 1. Suba um PostgreSQL

```bash
docker run --rm -d --name smartrent-pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:16
```

### 2. Informe as credenciais por variável de ambiente

Nenhuma credencial fica no código-fonte (RNF03).

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/postgres"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="postgres"
export JWT_SECRET="troque-por-um-segredo-de-no-minimo-32-bytes"
```

No Windows (PowerShell), use `$env:DATABASE_URL="..."` e assim por diante.

### 3. Rode a aplicação

```bash
./mvnw spring-boot:run
```

No primeiro boot o Flyway aplica as migrations de `src/main/resources/db/migration`
e o Hibernate valida o mapeamento contra o esquema resultante.

### 4. Acesse

| Página | Endereço |
|---|---|
| Painel e precificação | http://localhost:8080/index.html |
| Catálogo de imóveis | http://localhost:8080/imoveis.html |
| Gestão de reservas | http://localhost:8080/reservas.html |
| Entrar / criar conta | http://localhost:8080/login.html |

O site é público por padrão: dá para navegar sem login. A autenticação identifica
quem está usando, sem bloquear visitantes.

### Testes

```bash
./mvnw test
```

A suíte não depende de banco nem de rede.

## 📁 Estrutura do repositório

```
├── src/                         Aplicação Spring Boot (código, telas e migrations)
├── docs/
│   ├── STATUS_PROJETO.md        Acompanhamento das entregas
│   ├── documentacao-tecnica.md  Documentação técnica consolidada
│   ├── adr/                     Registros de decisão de arquitetura
│   ├── entregas/                Documentos entregues na disciplina
│   ├── design/                  PRD, guia de design e telas dos protótipos
│   └── relatorios-fonte/        Código que gera os 6 PDFs acadêmicos
├── entrega-isi/                 Script de implantação (AV03)
└── .github/workflows/           Integração contínua
```

Os PDFs em `docs/relatorios-fonte/out/` **não são editados à mão**: são gerados
pelos scripts Python daquela pasta. Veja o `README.md` dela antes de alterá-los.
