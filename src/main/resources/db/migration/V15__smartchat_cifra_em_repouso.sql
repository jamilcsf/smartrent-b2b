-- =====================================================================
-- SmartChat: cifra em repouso (AES-256-GCM na aplicacao, ver ADR-006).
-- O texto cifrado ("v1:" + Base64) e maior que o original e texto_original
-- tinha limite de 2000 caracteres: as tres colunas passam a TEXT. Os limites
-- de tamanho continuam validados pela aplicacao (SMARTCHAT_MAX_CARACTERES, 2000, 1000).
-- Os valores ja gravados em texto puro sao cifrados pelo job MigracaoCifraChat,
-- que le valor sem prefixo de versao como legado.
-- =====================================================================

alter table smartchat_mensagens alter column texto_filtrado type text;
alter table smartchat_mensagens alter column texto_original type text;
alter table smartchat_denuncias alter column descricao type text;
-- A notificacao do app pode trazer a previa (80 caracteres) de uma mensagem do chat: tambem cifrada.
alter table notificacoes alter column mensagem type text;
