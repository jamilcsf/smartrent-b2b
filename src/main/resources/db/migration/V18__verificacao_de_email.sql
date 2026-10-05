-- =====================================================================
-- Verificacao de e-mail: para enviar mensagem ou abrir conversa no SmartChat o
-- e-mail da conta precisa estar verificado. Contas JA existentes ficam
-- verificadas (nao trava os dados de demonstracao); novas contas por senha
-- nascem sem verificacao e recebem um link de uso unico. NAO e verificacao de
-- identidade: nada confere documento.
-- Como em troca_email, o token em si nunca e gravado: so o SHA-256.
-- =====================================================================

alter table usuarios add column email_verificado_em timestamptz;
update usuarios set email_verificado_em = now();

create table verificacao_email (
    id bigserial primary key,
    usuario_id bigint not null references usuarios (id),
    token_hash varchar(64) not null,
    criada_em timestamptz not null,
    expira_em timestamptz not null,
    usado_em timestamptz,
    cancelada_em timestamptz,
    constraint uk_verificacao_email_token unique (token_hash)
);
-- No maximo um link pendente por usuario (o reenvio substitui o anterior).
create unique index uk_verificacao_email_pendente on verificacao_email (usuario_id)
    where usado_em is null and cancelada_em is null;
