-- =====================================================================
-- Moderacao pelo painel de admin (ADR-010).
--  * moderacao_acoes: trilha IMUTAVEL de tudo que a administracao faz sobre uma conta (suspensao, reativacao, mensagem,
--    decisao de denuncia, revisao de alerta automatico, abertura de denuncia). Guarda o motivo/nota interna (texto curto
--    e sanitizado), nunca conteudo de mensagem do SmartChat.
--  * comunicados: caixa de entrada de avisos da plataforma para QUALQUER usuario (cliente, gestor ou admin), enviados pela
--    administracao em nome de um nivel (administracao, moderacao, suporte, seguranca). O destinatario so le os proprios.
-- =====================================================================

create table moderacao_acoes (
    id bigserial primary key,
    usuario_id bigint not null references usuarios (id),
    admin_id bigint not null references usuarios (id),
    tipo varchar(30) not null,
    motivo varchar(500),
    denuncia_id bigint references smartchat_denuncias (id),
    alerta_id bigint references alertas_internos (id),
    criado_em timestamptz not null
);
create index idx_moderacao_usuario on moderacao_acoes (usuario_id, criado_em desc);
create index idx_moderacao_criado on moderacao_acoes (criado_em desc);
create index idx_moderacao_denuncia on moderacao_acoes (denuncia_id);
create index idx_moderacao_alerta on moderacao_acoes (alerta_id);

create table comunicados (
    id bigserial primary key,
    destinatario_id bigint not null references usuarios (id),
    remetente_admin_id bigint not null references usuarios (id),
    remetente_nivel varchar(20) not null,
    assunto varchar(150) not null,
    texto text not null,
    denuncia_id bigint references smartchat_denuncias (id),
    criado_em timestamptz not null,
    lido_em timestamptz
);
create index idx_comunicados_destinatario on comunicados (destinatario_id, lido_em, criado_em desc);
create index idx_comunicados_criado on comunicados (criado_em desc);
