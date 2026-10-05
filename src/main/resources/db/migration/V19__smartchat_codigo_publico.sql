-- =====================================================================
-- SmartChat: identificador publico (UUID) da conversa. As rotas e os DTOs
-- passam a usar codigo_publico; o id sequencial deixa de sair da API.
-- Linhas existentes recebem um UUID aleatorio (gen_random_uuid, nativo desde o
-- PostgreSQL 13).
-- =====================================================================

alter table smartchat_conversas add column codigo_publico uuid;
update smartchat_conversas set codigo_publico = gen_random_uuid();
alter table smartchat_conversas alter column codigo_publico set not null;
alter table smartchat_conversas add constraint uk_smartchat_conversa_codigo unique (codigo_publico);
