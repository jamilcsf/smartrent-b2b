# ADR-004 — Calendário, bloqueios, SmartChat, cancelamento, vídeos e fuso horário

**Status:** Aceita
**Data:** 2026-10-03
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

Depois do fluxo de anúncios ([ADR-003](ADR-003-ciclo-de-vida-do-anuncio.md)) faltavam: o gestor operar suas
reservas, bloquear datas, conversar com o cliente sem sair da plataforma, cancelar e reembolsar com regra clara,
tratar vídeos com segurança e ter um fuso horário único. O repositório **não tinha** gateway de pagamento,
reserva feita pelo cliente, vínculo entre reserva e usuário, SSE/WebSocket nem FFmpeg. Esta ADR registra o que
foi decidido e o que ficou como suposição.

## Decisões

1. **Fuso único.** `smartrent.timezone` (`APP_TIMEZONE`, padrão `America/Sao_Paulo`) alimenta o `Clock` da
   aplicação, que agora usa esse nome IANA e não o fuso da JVM. Instantes **novos** são `Instant` em UTC
   (`timestamptz`); datas de calendário (check-in/out, bloqueios) são `date`; colunas antigas `timestamp` seguem
   como horário local da plataforma (não houve migração destrutiva). Durações (24h, 2h, lembretes, expiração) somam
   **tempo decorrido** (`Agora.somar`), então continuam certas se um dia tiver 23 ou 25 horas. O front formata e decide
   "hoje" em Brasília (`UI.instante`, `UI.dataHora`, `GET /api/config/agora`).
2. **Dashboard e Painel.** "Portal da transparência" virou **Dashboard** (`/estatisticas.html`, só estatísticas do
   gestor autenticado, com Chart.js); a rota antiga redireciona. O **Painel do Gestor** concentra criar anúncio,
   reservas, calendário, bloqueios, pré-publicação e a sugestão de preço por IA (suposição do enunciado).
   Ocupação = noites confirmadas ou concluídas ÷ noites disponíveis; **bloqueios saem do denominador** e aparecem à
   parte; receita líquida = receita paga − reembolsos processados.
3. **Campos comerciais.** Mínimo de diárias e taxa de limpeza são novos; o "limite de hóspedes" já existia
   (`capacidade_hospedes`) e foi reutilizado (teto de 50). A reserva grava de forma imutável mínimo, taxa, limite e
   os parâmetros da política de cancelamento; o servidor valida mínimo e limite sempre.
4. **Reserva do cliente e gateway simulado.** Como não havia fluxo de cliente nem pagamento, criou-se
   `Reserva.cliente`, `GatewayPagamento` (interface) e um sandbox idempotente. A chave de idempotência é **única no
   banco** (cobrança e estorno). Reserva pendente expira em 30 min sem cobrança. Nenhum dado de cartão é coletado.
5. **Calendário e bloqueios.** Três meses por imóvel, "hoje" do servidor. Bloqueio usa datas **inclusivas** e é
   seguro contra concorrência: reserva e bloqueio travam a mesma linha do imóvel (`PESSIMISTIC_WRITE`) e checam
   conflito na mesma transação (teste com duas threads). Reservas confirmadas impedem o bloqueio (com opção de
   bloquear só as livres); pendentes só saem, **sem cobrança**, se o gestor confirmar. Motivo e observação são
   internos; clientes veem apenas "indisponível".
6. **Cancelamento e reembolso.** `RefundPolicyService` é uma cadeia ordenada de regras (gestor, pendente,
   [arrependimento: ponto de extensão sem efeito], antecedência). Prazo = 48 h antes do **horário** de check-in (14:00
   de Brasília, configurável), "até" inclusive. Reembolso integral inclui a taxa de limpeza e não retém taxa do meio
   de pagamento (suposição). Falha do gateway **não desfaz** o cancelamento: o reembolso fica `FALHA`, há retry com
   espera crescente e alerta ao gestor. Estados novos: `CANCELADA_COM_REEMBOLSO`, `CANCELADA_SEM_REEMBOLSO`,
   `CANCELADA_PELO_GESTOR`; reserva pendente cancelada/expirada usa `CANCELADA_SEM_REEMBOLSO` com regra
   `PENDENTE_SEM_COBRANCA`.
7. **SmartChat.** Conversa única por cliente + gestor + imóvel (restrição no banco). Criada automaticamente depois do
   commit da reserva (`@TransactionalEventListener(AFTER_COMMIT)` em transação nova): falha registra erro e a
   reconciliação (job de 5 min) refaz, **sem desfazer a reserva**. Mensagem de sistema com chave de idempotência única.
   A aba do cliente é a flag `usuarios.smartchat_liberado` (no backend). Tempo quase real por **SSE com ticket de uso
   único** (EventSource não envia `Authorization`) e polling como alternativa; o evento não leva texto.
8. **Filtro de conteúdo.** Roda no servidor ao receber a mensagem: telefones (com/sem DDD, +55, separadores, disfarçados,
   por extenso, com letras), e-mails, links externos (domínios próprios permitidos por configuração), ofensas e termos
   sexuais (lista configurável, tolerante a acento, repetição, símbolos e espaçamento). O trecho vira um marcador com
   **preenchimento aleatório**; o original fica só em `texto_original` (sem getter em DTO). Datas, horas, R$, quantidades,
   códigos IMV/RES e CEP são mascarados antes da busca. Mensagem de sistema é isenta. A moderação de imagens tem interface
   (`ContentModerationService.moderarImagem`) mas **não está implementada** (sem anexos nesta etapa).
9. **Denúncia e bloqueio (protótipo).** Persistem em tabelas próprias para o futuro módulo de administração;
   `SMARTCHAT_BLOCK_ENFORCEMENT=false` mantém o chat funcionando e o texto da interface não afirma efeito inexistente.
   O ponto único de aplicação futura é `SmartChatService.exigirSemBloqueio`.
10. **Vídeos.** Limite de **1:30** (90 s), validado no servidor; até 2 vídeos fora das 14 imagens. Envio **em partes
    retomável** (o servidor é o dono do offset), validação no `concluir` (tipo real pelo conteúdo, tamanho, trilha de
    vídeo, duração, resolução) e **processamento assíncrono** por worker com fila em banco, retry e estados
    `ENVIANDO/PROCESSANDO/PRONTO/FALHA`. `VideoProcessingService` tem a implementação FFmpeg (H.264+AAC faststart até
    1080p, HLS 360/720/1080, poster, `-map_metadata -1`, argumentos em lista, sem shell, com tempo máximo) e um fallback
    básico (sem FFmpeg). Publicar e confirmar alteração exigem todos os vídeos prontos; só `PRONTO` aparece ao público;
    na edição o vídeo antigo permanece até a confirmação. Não usamos biblioteca de HLS no navegador: HLS onde há suporte
    nativo e MP4 nos demais. Upload pré-assinado direto não se aplica ao disco local (ver Consequências).
11. **WhatsApp removido.** O código parou de ler e gravar `imoveis.whatsapp_link` e `reservas.hospede_telefone`; a V12
    arquiva os valores e avisa (in-app) os gestores com anúncio no ar; a V13, separada, descarta as colunas. Rascunhos
    antigos continuam legíveis (`@JsonIgnoreProperties`). Um teste varre o código a cada build.

## Suposições a validar

- Reembolso integral = total do snapshot, sem retenção de taxa de pagamento; prazo em 48 h do horário de check-in.
- Reserva pendente do cliente expira em 30 minutos (configurável).
- Bloqueio não pode começar em data passada; limite de 3 anos à frente.
- "Concluir o cadastro" do enunciado foi tratado como **publicar** e **confirmar alteração** (bloqueados com vídeo não
  pronto); salvar rascunho e confirmar preço continuam permitidos.
- E-mail, além de telefone e link, é ocultado no chat (categoria própria).
- Estatísticas atribuem receita ao mês do check-in.

## Pendências que dependem do setor jurídico (apenas listadas, sem decisão)

- Texto definitivo da política de cancelamento e dos termos; hoje há um texto **provisório** em
  `textos-pendentes-juridico.properties`, claramente marcado.
- Direito de arrependimento (CDC, art. 49): ponto de extensão `CANCEL_REGRET_DAYS=0` (desligado e sem efeito).
- **Item 5 do termo de uso** (`TermoUso`) ainda cita o WhatsApp, canal removido; não foi reescrito. É preciso decidir o
  novo texto, a versão do termo e se os anunciantes aceitam de novo.

## Consequências

- Mídia em disco local e `MidiaStorage.caminhoLocal` impedem hoje o upload pré-assinado; com S3, trocar por multipart
  pré-assinado mantendo o contrato de estados.
- Rate limit e agrupamento de notificações do chat estão em memória (uma instância); distribuir quando houver mais de uma.
- O gateway é simulado: antes de produção, implementar `GatewayPagamento` de um provedor real.
- A suíte passou a ter testes de integração em H2 (reservas, chat, vídeos, concorrência) além dos unitários; as
  migrations V7–V13 foram validadas contra PostgreSQL 16, inclusive com dados legados de WhatsApp.
- Ainda sem testes automatizados do front-end (JavaScript).
