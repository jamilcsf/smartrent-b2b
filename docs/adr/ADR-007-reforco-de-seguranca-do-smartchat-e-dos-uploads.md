# ADR-007 — Reforço de segurança do SmartChat e dos uploads

**Status:** Aceita
**Data:** 2026-10-05
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

Depois da cifragem em repouso ([ADR-006](ADR-006-cifragem-em-repouso-do-smartchat.md)), o SmartChat e os uploads ainda
tinham lacunas de segurança: nada lembrava o usuário de não pagar nem passar contato fora da plataforma; as fotos de
anúncio eram publicadas como chegavam do celular (com as coordenadas de GPS); mensagens que tentam levar o negócio para
fora da plataforma ou que são disparadas em massa passavam sem qualquer sinal para a equipe; qualquer conta nova, com
qualquer e-mail, podia escrever; e as conversas eram endereçadas por id sequencial. Esta ADR registra o reforço dessas
seis frentes. Tudo é compatível com a cifragem em repouso: nenhuma query nova toca as colunas cifradas.

## O que já existia (confirmado pelos testes, não refeito)

- Mascaramento no servidor de telefone, e-mail e link externo (`MessageFilterService`; ADR-004, decisão 8).
- Tipo real da imagem pelos primeiros bytes (`MidiaProcessador`, `FotoPerfilProcessador`).
- Remoção de EXIF **na foto de perfil** (ADR-005, decisão 4).
- Vídeo com FFmpeg: `-map_metadata -1` e `-map_chapters -1` (teste `VideoProcessingFfmpegTest`).
- Checagem de participante nas conversas. **Ressalva:** ela existia, mas quem não participava recebia **403** (e conversa
  inexistente, 404), o que revelava quais conversas existem. Isso foi corrigido na decisão 6.

## Decisões

1. **Aviso fixo no chat.** Faixa `role="note"` acima da lista de mensagens (fora da área que rola), sem botão de fechar,
   com o texto exato "Para sua segurança, nunca faça pagamentos ou compartilhe contatos fora da nossa plataforma."
   Tailwind (âmbar escuro sobre âmbar claro, contraste alto), igual ao resto das telas e funcional no celular. O JS não a
   referencia, então não há como escondê-la.
2. **Fotos de anúncio sem metadados.** `ImagemSegura` extrai de `FotoPerfilProcessador` as peças comuns (tipo real,
   orientação EXIF, JPEG/PNG sem metadados). `MidiaProcessador.sanearImagem` recodifica **toda** imagem de anúncio antes de
   gravar: aplica a orientação aos pixels e grava só o mínimo (sem EXIF, GPS, XMP, comentários nem miniatura embutida; PNG
   sem blocos `tEXt`/`eXIf`). JPEG continua JPEG (qualidade 0,92) e PNG continua PNG, com transparência. Limites de pixels,
   tamanho e a geração de miniatura seguem como estavam. **Conflito com a regra de 360: não houve**, com um ajuste: a regra
   2:1 passou a valer para a imagem **como será exibida** (com a orientação aplicada). O viewer 360 usa o tipo `FOTO_360` e a
   proporção, não o XMP `GPano`, então apagar o XMP não afeta a exibição. `V16` + `SaneamentoFotosAnuncio` reprocessam, uma
   única vez, as fotos já armazenadas: a coluna `imovel_midias.metadados_removidos` marca o que já foi tratado, então rodar de
   novo não recodifica (o que degradaria o JPEG). Registra só contagens. **Vídeos:** no caminho FFmpeg já se remove todo
   metadado (inclusive localização); no processamento **básico** (sem FFmpeg) o MP4 é publicado como veio, **com**
   metadados. Em produção o FFmpeg é obrigatório para isso (já era recomendado).
3. **Termos associados a fraude.** Nova categoria `SUSPEITA_FRAUDE` na mesma lista de termos
   (`smartchat-termos.txt` / `SMARTCHAT_TERMOS_ARQUIVO`) e com a mesma tolerância a acento, repetição, símbolos e
   espaçamento. **Não bloqueia nem mascara:** a categoria é gravada na mensagem, o DTO traz `suspeitaFraude` **só para o
   destinatário** (o autor nunca vê) e o front mostra o alerta "Esta mensagem menciona pagamento ou contato fora da
   plataforma. Pagamentos feitos por fora não têm proteção." Gera também um alerta interno (decisão 4). Mensagem de sistema
   segue isenta. Um arquivo próprio de termos sem linhas dessa categoria mantém a lista padrão de fraude.
4. **Análise de comportamento e alertas internos.** `AnaliseComportamentoChat` detecta o **mesmo texto normalizado**
   (caixa, acento, espaços e pontuação) do mesmo autor em **N conversas distintas em M minutos** (5 e 10 por padrão), com
   limiar menor para conta criada há menos de X dias (3 e 7). Compara um **HMAC-SHA-256 do texto normalizado**
   (`smartchat_mensagens.texto_hmac`), nunca o texto; chave em `SMARTCHAT_HMAC_KEY` (mesma regra da chave de cifra: sem ela,
   fora de `dev`/`test`, a aplicação não sobe). A entidade `AlertaInterno` (`alertas_internos`: usuário, tipo, contagem,
   janela, status, criado em) **não tem conteúdo de mensagem**; há um alerta por usuário e tipo na janela de alertas (60 min).
   Ao disparar envio em massa o usuário passa a ter limite de envio mais restrito (3 por minuto por 60 min) no
   `LimitadorDeTaxa` existente; **a conta não é suspensa**. A contagem de alertas abertos entra no `SinaisDeRisco`
   (`alertas_comportamento_abertos`), o mesmo caminho que já leva as denúncias à equipe; não há painel novo. `V17`.
5. **Verificação de e-mail para usar o chat.** `usuarios.email_verificado_em` e `verificacao_email` (`V18`). Cadastro por
   senha envia link de uso único e validade (24 h, `PERFIL_EMAIL_VERIFICACAO_HORAS`) com `TokenSeguro`, `EmailSender` e o
   padrão do `TrocaEmailService` (só o SHA-256 no banco; link inválido, vencido ou usado dá a mesma mensagem). Login pelo
   Google (que só entrega e-mail verificado) e confirmação de troca de e-mail contam como verificado. Reenvio com limite de
   taxa (`PERFIL_EMAIL_PEDIDOS_POR_HORA`). Contas existentes ficam verificadas na migração. Sem verificação o usuário **lê**
   as conversas, mas não envia mensagem nem abre conversa **nova** (regra em `SmartChatService`; HTTP 403); conversa já
   existente continua abrindo, e a mensagem de sistema e a criação automática pela reserva continuam funcionando. O perfil do
   chat mostra o selo "E-mail verificado". **Não há** "identidade verificada": nenhum documento é conferido.
6. **UUID no lugar do id sequencial.** `smartchat_conversas.codigo_publico` (`V19`, preenchido nas linhas existentes). Rotas
   `/api/smartchat/conversas/{codigo}/...`, `Conversa.codigo` no DTO, evento SSE `{conversa: uuid}` (sem `mensagemId`) e
   link `?conversa=<uuid>`. Nenhum DTO do chat traz id numérico de conversa ou de usuário. Conversa inexistente, de terceiros
   ou com identificador malformado (inclusive o antigo, numérico) dão **a mesma resposta 404**. O ticket do SSE já era um UUID
   aleatório.

## Auditoria das rotas fora do chat

Rotas que recebem id sequencial de **reserva, pagamento ou usuário** e como o dono é validado (nenhuma foi migrada para
UUID, conforme combinado):

| Rota | Id | Validação do dono |
|---|---|---|
| `GET /api/cliente/reservas/{id}` | reserva | `ReservaClienteService.buscar`: `reserva.cliente == usuário` |
| `POST /api/cliente/reservas/{id}/pagar` | reserva (o pagamento é criado por ela) | `ReservaClienteService.pagar`: mesmo teste, com a linha travada |
| `GET /api/cliente/reservas/{id}/simulacao-cancelamento` · `POST .../cancelar` | reserva | `CancelamentoService.doCliente`: mesmo teste |
| `GET\|PUT\|DELETE /api/reservas/{id}` · `POST .../cancelar` | reserva | papel gestor/admin (`SecurityConfig`) + `ReservaService.doGestor` / `CancelamentoService`: `acesso.conferir(gestor, imóvel)` |
| `POST /api/smartchat/conversas/por-reserva/{id}` | reserva | cliente da reserva ou gestor do imóvel; senão recusa |
| `POST /api/gestor/notificacoes/{id}/lida` | notificação | `NotificacaoService.marcarLida`: `notificação.usuário == usuário` |
| `/api/gestor/imoveis/{id}/...` · `/midias/{midiaId}` · `/videos/uploads/{midiaId}` · `/imoveis/{imovelId}/bloqueios/{bloqueioId}` · `/calendario?imovelId` | imóvel, mídia, bloqueio | `ImovelAcesso.doGestor(ParaAtualizar)`; mídia e bloqueio só valem se pertencem ao imóvel informado |
| qualquer rota com id de **usuário** | — | não existe: o perfil age sempre sobre o usuário autenticado (ADR-005, decisão 2) |
| qualquer rota com id de **pagamento** | — | não existe: o pagamento nasce e é consultado pela reserva (`/pagar`), que valida o dono |

**Nenhuma rota permite acesso a dado de terceiros; nada foi corrigido.** Duas observações, não corrigidas por não darem
acesso a dado:

- As rotas de reserva e de notificação respondem **403** ("não é sua") para o que é de outro usuário e **404** para o que não
  existe. Com ids sequenciais isso permite descobrir quais ids existem. A correção é devolver o mesmo 404 (como foi feito no chat).
- `GET /api/precificacao/sugerir?imovelId=` é **público** e **grava** uma sugestão de preço para qualquer imóvel (rota
  legada do stub de IA, `permitAll`). Não devolve dado de terceiros, mas é um endpoint sem autenticação com efeito colateral.

## Decisões tomadas sem estar especificadas

- Limiar de conta nova: 3 conversas (configurável), em vez de "peso" numérico; mínimo 2.
- Textos normalizados com menos de 8 caracteres ("ok", "obrigado") não entram na comparação de envio em massa.
- Janela de não repetição dos alertas: 60 minutos (`SMARTCHAT_ALERTA_JANELA_MINUTOS`), separada da janela M de contagem.
- O alerta interno de fraude usa a mesma entidade, com tipo `SUSPEITA_FRAUDE`; só o de envio em massa restringe o limite.
- Alertas internos só sinalizam: entram no `SinaisDeRisco` como contagem, **não** como impedimento de aprovação.
- Verificação de e-mail vale também para gestores, e o link tem validade de 24 h (o link de troca de e-mail é de 60 min).
- O campo do DTO se chama `verificado` (e não `emailVerificado`) para manter a regra dos testes de que nenhum DTO do chat
  tem componente com "email" no nome: o endereço nunca sai.
- Não-participante passou de 403 para 404 (era o único comportamento que contradizia o pedido de resposta idêntica).

## Visão Futura (V2)

Fora do escopo desta entrega, registrado sem implementação:

- **Envio de imagens e comprovantes pelo chat.** Exige moderação de imagem (a interface existe, a implementação não),
  armazenamento, remoção de EXIF e política de retenção; hoje o chat é só texto de propósito.
- **Verificação por telefone (SMS) e por documento, e o selo de identidade verificada.** Depende de provedor, custo e base
  legal (LGPD) para tratar documento; sem isso, dizer "identidade verificada" seria enganoso. Por isso o selo atual é só
  "E-mail verificado".
- **Classificação de fraude por IA.** A lista de termos é explicável e barata; um classificador exigiria enviar texto de
  conversa a um serviço externo (o que a cifragem em repouso e o ADR-006 procuram evitar), avaliar falsos positivos e definir
  quem decide sobre a conta.

## Pendências

- Revisão jurídica do texto do aviso fixo, do alerta de fraude e do e-mail de verificação (estão em
  `textos-pendentes-juridico.properties` apenas os do e-mail; os do chat estão no front).
- SMTP real: o link de verificação só sai em log (`EMAIL_LOG_CORPO`), como os demais.
- Retenção de `texto_hmac` e dos alertas internos (hoje sem prazo); definir com o jurídico junto da retenção de `texto_original` (ADR-006).
- Metadados no vídeo do modo **básico** (sem FFmpeg).
- Rate limit e limite restrito em memória (uma instância), como o resto (ADR-004).
- Uso de memória do saneamento: uma imagem de 60 megapixels é decodificada inteira (centenas de MB de heap); se o servidor for pequeno, reduzir o teto de pixels.

## Consequências

- Novas variáveis: `SMARTCHAT_HMAC_KEY` (obrigatória fora de `dev`/`test`), `SMARTCHAT_ENVIO_MASSA_*`, `SMARTCHAT_CONTA_NOVA_DIAS`,
  `SMARTCHAT_LIMITE_RESTRITO_*`, `SMARTCHAT_ALERTA_JANELA_MINUTOS`, `PERFIL_EMAIL_VERIFICACAO_HORAS`, `MIDIA_SANEAR_AO_INICIAR`.
- Quem chamava `/api/smartchat/conversas/{id}` com id numérico (links antigos de notificação, favoritos) recebe 404.
- Quem se cadastra por senha precisa confirmar o e-mail antes de escrever; os dados de demonstração já nascem verificados.
- As fotos de anúncio passam a ser recodificadas: o arquivo publicado não é mais idêntico ao enviado (JPEG com perda leve controlada).
- As migrations V15 a V19 foram aplicadas em PostgreSQL 16 com `ddl-auto=validate`, e o fluxo foi exercitado contra a API em execução.
