# ADR-010 — Moderação no painel de admin: denúncias, decisões automáticas, suspensão e avisos

**Status:** Aceita
**Data:** 2026-10-08
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

As denúncias do SmartChat eram só gravadas ("para o futuro módulo de administração"), os alertas automáticos de comportamento
(envio em massa, suspeita de fraude) não tinham tela, o "bloqueio" era um protótipo sem efeito e a única ação sobre uma conta era o
liga/desliga da [ADR-009](ADR-009-painel-admin-e-telemetria-de-uso.md). Pediu-se uma área dedicada para administrar denúncias, ver as
decisões automáticas, suspender/reativar contas e **falar diretamente com os usuários** em nome da plataforma e de outros níveis.

## Decisões

### 1. Área `/moderacao.html` (só ADMIN) e `/api/admin/moderacao/**`

Abas: **Denúncias** · **Decisões automáticas** · **Contas suspensas** · **Avisos enviados** · **Histórico**. Atalhos globais: enviar
mensagem a qualquer usuário e suspender uma conta. A página de Usuários do painel usa o mesmo módulo de ações (`moderacao-acoes.js`).

### 2. Toda ação exige justificativa e deixa trilha imutável (V22)

`moderacao_acoes` guarda quem, o quê, em quem, a justificativa (≤ 500 caracteres, sanitizada) e a referência (denúncia/alerta). Não guarda
conteúdo de mensagem do SmartChat. Suspensão e reativação também vão para `auditoria_conta`. Regras mantidas da ADR-009: o admin não
altera a própria conta nem a de outro admin.

### 3. Denúncias

- Fluxo: `PENDENTE` → `PROCEDENTE` | `IMPROCEDENTE` (uma decisão só; repetir dá 409). Não há status intermediário de propósito: o
  impedimento da exclusão de dados (ADR-005) conta denúncias `PENDENTE`.
- **Evidência proporcional:** o admin vê só as mensagens que o denunciante **anexou**, no texto que o destinatário viu (trechos ocultados
  continuam ocultos), a descrição e o contexto do denunciado (denúncias anteriores, alertas automáticos, ações anteriores). **Abrir o
  detalhe é registrado** (`DENUNCIA_ABERTA`).
- Decisão procedente pode **suspender o denunciado** na mesma operação e **avisar** denunciante e/ou denunciado com texto padrão (o
  denunciante não é informado da sanção aplicada; o denunciado só é avisado em denúncia procedente, nunca em improcedente).

### 4. Decisões automáticas: ver, revisar e reverter

O que o sistema decide sozinho no SmartChat, agora visível: (1) **oculta** telefone, e-mail, link, contato externo e conteúdo impróprio;
(2) **reduz o limite de envio** de quem manda o mesmo texto a várias conversas (alerta `ENVIO_EM_MASSA`); (3) **sinaliza** pagamento ou
contato fora da plataforma (`SUSPEITA_FRAUDE`, sem restrição). A tela mostra cada alerta com a explicação e o **efeito aplicado**
("Limite de envio reduzido a N mensagens/min até HH:mm"), mais um resumo do filtro (por dia, por tipo e por usuário) com **só contagens,
nunca texto**.

Revisão de alerta: **Confirmar** (`REVISADO`, mantém o efeito até o prazo) ou **Descartar** (`DESCARTADO`, falso positivo). **Descartar tem
efeito real:** o limite de envio deixa de valer (`AnaliseComportamentoChat.limiteRestrito` ignora alertas descartados); a deduplicação de
alertas continua enxergando o descartado (não gera alerta repetido na mesma janela). É um canal de revisão humana para decisões
automatizadas, no espírito do art. 20 da LGPD (a análise jurídica fica pendente, ver abaixo).

### 5. Suspensão = conta inativa (mecanismo que já existia)

Suspender põe `usuarios.ativo = false`: o filtro JWT recusa tokens na hora e o login é recusado. **Novidade:** anúncios de gestor suspenso
saem do catálogo e do detalhe público (`ImovelRepository.VISIVEL` passou a exigir `usuario.ativo`). Reservas já confirmadas não são tocadas.
A reativação desfaz tudo. O usuário é avisado (aviso + e-mail) com o motivo e como contestar.

### 6. Avisos da administração (`comunicados`, V22) em nome de um nível

Qualquer usuário (cliente, anfitrião ou admin) recebe avisos na caixa **Avisos** (`/comunicados.html`, item no menu com contador de não
lidos, `/api/comunicados/**` só para o próprio logado; aviso de outra pessoa dá 404). O admin escolhe **em nome de quem fala**:
*Administração da plataforma*, *Moderação*, *Suporte* ou *Segurança e privacidade* (enum `NivelRemetente`). Há opção de cópia por
e-mail (falha de e-mail nunca desfaz a ação). Assunto (3–150) e texto (5–2000) são sanitizados; limite de 30 avisos por hora por admin;
cada envio entra na trilha (`MENSAGEM`).

> **Suposição a confirmar:** "demais níveis de gerência" foi interpretado como **o nível em nome do qual o aviso é enviado** (e o aviso
> alcança todos os papéis). Não foi criada hierarquia de permissões entre admins (todo ADMIN pode tudo). Se a intenção era níveis de
> acesso (ex.: moderador que só vê denúncias, gerente que também suspende), é uma extensão da V2 abaixo.

## Limites e pendências

- **Jurídico:** texto dos avisos automáticos, prazo para contestação, base legal e tratamento de decisões automatizadas (LGPD art. 20)
  dependem do setor jurídico; os textos são provisórios no código (`ModeracaoService`).
- O botão "bloquear usuário" do chat continua protótipo (`SMARTCHAT_BLOCK_ENFORCEMENT=false`); a suspensão de conta é o mecanismo efetivo.
- Mensagens de usuário a usuário seguem sem leitura pelo admin, exceto as anexadas a uma denúncia.
- Suspender não cancela reservas existentes do gestor nem reembolsa; decisão operacional/jurídica a tomar caso a caso.

## Visão Futura (V2)

- Níveis de acesso entre administradores (moderador, gerente, superadmin) e aprovação em duas pessoas para suspensões.
- Prazo/automação de suspensão temporária (data de fim) e fila de contestações respondidas pelo usuário.
- Resposta do usuário ao aviso (hoje é só leitura; o contato é por e-mail/suporte).
- Painel de decisões de outras fontes automáticas (precificação por IA, expiração de reservas pendentes).
