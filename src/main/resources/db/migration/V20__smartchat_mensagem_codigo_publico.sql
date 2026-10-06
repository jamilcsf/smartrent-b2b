-- =====================================================================
-- SmartChat: identificador publico (UUID) da mensagem. O id sequencial deixa de sair da API: o cursor de
-- paginacao (depoisDe), a lista de mensagens e as denuncias passam a usar codigo_publico. As linhas existentes
-- recebem um UUID aleatorio (gen_random_uuid, nativo desde o PostgreSQL 13), como em V19 para as conversas.
-- =====================================================================

alter table smartchat_mensagens add column codigo_publico uuid;
update smartchat_mensagens set codigo_publico = gen_random_uuid();
alter table smartchat_mensagens alter column codigo_publico set not null;
alter table smartchat_mensagens add constraint uk_smartchat_mensagem_codigo unique (codigo_publico);
