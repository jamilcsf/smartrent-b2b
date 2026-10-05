# ADR-006 — Cifragem em repouso das mensagens do SmartChat

**Status:** Aceita
**Data:** 2026-10-05
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

O SmartChat ([ADR-004](ADR-004-calendario-smartchat-cancelamento-video-e-fuso.md), decisões 7 a 9) guardava o texto
das mensagens em claro no PostgreSQL (Supabase): `smartchat_mensagens.texto_filtrado` (o que o usuário vê),
`smartchat_mensagens.texto_original` (restrito ao backend, insumo de moderação) e `smartchat_denuncias.descricao`
(texto livre do denunciante). Quem tivesse acesso ao banco, a um backup ou ao painel do Supabase lia tudo.

Esta ADR registra a **cifragem em repouso** desses três campos e da prévia de mensagem guardada em `notificacoes.mensagem`. O que ela **não** é: o servidor continua lendo o
texto, porque o filtro de conteúdo roda nele e a denúncia exige o original (ADR-004, decisões 8 e 9). Nada disso mudou.

## Decisões

1. **Cifra no código da aplicação.** `CifraCampo` usa `AES/GCM/NoPadding` do JDK (`javax.crypto`, nenhuma dependência
   nova), chave de 256 bits, IV aleatório de 12 bytes por valor (`SecureRandom`) e tag de 128 bits. O GCM detecta
   adulteração: valor alterado ou chave errada lança `FalhaDecifragemException`; nunca se devolve lixo nem se engole o erro.
2. **Formato gravado:** `"v1:" + Base64(IV || ciphertext+tag)`. O prefixo é a versão da chave e abre caminho para
   rotação futura (ler `v1`, gravar `v2`). `null` entra e `null` sai.
3. **Legado tolerado.** Na leitura, valor **sem** prefixo de versão (`vN:`) é texto puro antigo e volta como está; é
   o que mantém o sistema funcionando entre a migration e a migração dos dados. Valor com prefixo e tag inválida
   lança exceção.
4. **Aplicação transparente por `AttributeConverter` do JPA** (`TextoCifradoConverter`) em `textoFiltrado`,
   `textoOriginal`, `DenunciaChat.descricao` e `Notificacao.mensagem` (a prévia de até 80 caracteres, que ficaria em claro; a prévia
   continua aparecendo, o produto não muda). `SmartChatService` e os DTOs não mudaram. Conferido: nenhuma query
   (derivada, JPQL ou nativa) filtra ou ordena por essas colunas; as consultas de `SmartChatMensagemRepository` e
   `DenunciaChatRepository` usam só ids, status, tipo, datas e vínculos. **Regra daqui em diante: não filtrar, ordenar
   nem buscar por essas colunas** (o banco só vê texto cifrado; busca exigiria outra decisão).
5. **Chave por ambiente.** `smartrent.chat.crypto-key`, lida de `SMARTCHAT_CRYPTO_KEY` (Base64 de 32 bytes), no padrão das
   demais `smartrent.chat.*`. Fora dos perfis `dev` e `test` a aplicação **não sobe** sem a chave ou com tamanho errado
   (mensagem clara, sem repetir o valor). Nos perfis `dev`/`test`, sem chave, usa-se uma chave **pública** de
   desenvolvimento embutida, com aviso no log. O `ChatProperties.toString()` mascara a chave. A chave nunca vai a log.
6. **Schema.** Migration `V15` (Flyway, o mesmo mecanismo das anteriores; `ddl-auto=validate` segue valendo) passa as
   as colunas para `TEXT`: o texto cifrado é maior e `texto_original` tinha limite de 2000. Os limites de tamanho
   continuam validados pela aplicação.
7. **Migração dos dados existentes.** `MigracaoCifraChat` percorre as tabelas por id, em lotes de 500 (uma transação por
   lote), via JDBC (por JPA o conversor decifraria e recifraria, e o *dirty checking* não veria diferença) e cifra **só**
   texto puro: valor sem prefixo de versão ou com prefixo que não decifra (legado como "v1: teste"; só a migração faz essa leitura
   branda, a leitura normal continua lançando exceção). É idempotente, roda no boot enquanto `SMARTCHAT_CRYPTO_MIGRATE_ON_START=true` (padrão) e
   registra **apenas contagens**. Se falhar no meio, registra o tipo da exceção e retoma na próxima execução.
8. **Logs.** Revisados `SmartChatService`, `ChatEventos`, `ChatReconciliador`, `ChatReservaListener`,
   `ContentModerationService` e as notificações: nenhum registrava texto de mensagem, **com uma exceção**: o e-mail de
   "Nova mensagem" levava a prévia (80 caracteres) no corpo e o `NotificadorEmailLog` grava o corpo em log. O e-mail passou
   a dizer só "Você recebeu uma nova mensagem no SmartChat."; a prévia da notificação no app passou a ser cifrada (decisão 4).
9. **Trânsito.** `DATABASE_URL` de produção deve usar `sslmode=require` (documentado no README); a aplicação não força isso.

## Por que E2EE foi descartado (Visão Futura, V2)

E2EE (cifragem entre os dois usuários, com chaves só nos dispositivos) é incompatível com o que o produto promete hoje:
o filtro que oculta telefone, e-mail, link e ofensas **roda no servidor** e precisa ler o texto; a denúncia precisa do
conteúdo para a equipe analisar; e a prévia/notificação e a moderação futura de imagens têm a mesma necessidade. Fazer E2EE
exigiria mover o filtro para o cliente (que o usuário pode contornar), gerenciar chaves por dispositivo e recuperação
de conta, e abrir mão da moderação no servidor. É uma mudança de produto, não de infraestrutura. Fica classificada como
**Visão Futura (V2)**, a decidir com o jurídico e o produto; o que foi entregue aqui é cifragem em repouso, e a
documentação não o chama de outra coisa.

## Modelo de ameaça

| Cenário | Protegido? |
|---|---|
| Vazamento do banco ou de um *dump*/backup | Sim, desde que a chave não vaze junto |
| Acesso direto ao Supabase (painel, SQL, funcionário, credencial do banco vazada) | Sim: só se vê texto cifrado |
| Leitura do tráfego aplicação ↔ banco | Só com `sslmode=require`; a cifra de campo ajuda, mas o restante dos dados trafega pelo TLS |
| Quem tem a **chave e** o banco ao mesmo tempo (ex.: acesso ao ambiente da aplicação) | **Não** |
| Invasão da aplicação em execução (a aplicação decifra) | **Não** |
| Operadores da aplicação e do filtro (servidor lê o texto por desenho) | **Não** |
| Adulteração de um valor | Detectada (GCM), mas não há vínculo à linha: quem escreve no banco pode **trocar** um valor cifrado válido por outro |
| Metadados (quem fala com quem, quando, tamanho aproximado, ocorrências, categorias, motivo da denúncia) | **Não**: seguem em claro |

## Pendências

**Decisões em aberto (não implementadas, a definir com o jurídico):**

- **Prazo de retenção de `texto_original`.** Hoje ele é guardado sem prazo. Definir por quanto tempo, e se é apagado ou anonimizado
  depois (inclusive o de mensagens já denunciadas).
- **Mensagens na exclusão de conta** ([ADR-005](ADR-005-perfil-seguranca-da-conta-e-exclusao-de-dados.md)). O
  `DataDeletionExecutor` continua sem implementação; falta decidir o destino das mensagens e denúncias de quem pede exclusão
  (apagar, anonimizar autor, reter por obrigação legal ou por denúncia aberta) e se a chave é descartada junto.

**Técnicas:**

- **Rotação de chave:** o formato prevê (`v1`, `v2`), mas não há segunda chave nem rotina de reencriptação.
- **Guarda e backup da chave:** ela vive em variável de ambiente; **perder a chave torna as mensagens irrecuperáveis**.
  Falta guardá-la em um cofre/gerenciador de segredos e definir quem a backupeia, separada do backup do banco.
- Qualquer novo campo de texto de chat (por exemplo, a moderação de imagens, ainda sem implementação, ADR-004) deve passar pelo mesmo conversor.

## Consequências

- Todo ambiente fora de `dev`/`test` precisa de `SMARTCHAT_CRYPTO_KEY` antes do deploy; localmente, `executar.bat` e
  `mvn spring-boot:run` com `SPRING_PROFILES_ACTIVE=dev` dispensam a chave.
- Quem consultar o banco à mão para suporte passa a ver `v1:...` nessas colunas.
- Custo de CPU desprezível (AES com instrução de hardware); o tamanho cresce ~33% (Base64) mais 28 bytes por valor.
- A V15 foi aplicada em PostgreSQL 16 junto com `ddl-auto=validate`, e a migração dos dados foi exercitada com uma linha legada.
