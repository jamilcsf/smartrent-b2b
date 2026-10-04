-- =====================================================================
-- SmartRent B2B — Perfil do usuario: foto, versao de sessao, troca de e-mail,
-- solicitacao de exclusao de dados (analise manual, nada e excluido
-- automaticamente) e auditoria de conta.
-- Datas novas em timestamptz (instantes em UTC). Nenhum valor sensivel
-- (senha, hash, token em claro) e gravado nestas tabelas.
-- =====================================================================

alter table usuarios add column foto_arquivo varchar(64);
alter table usuarios add column foto_versao integer not null default 0;
-- Incrementa a cada troca de senha/e-mail: tokens emitidos antes deixam de valer.
alter table usuarios add column sessao_versao integer not null default 0;
-- false = conta criada pelo Google, sem senha conhecida (reautentica com o Google).
alter table usuarios add column senha_definida boolean not null default true;

create table auditoria_conta (
    id bigserial primary key,
    usuario_id bigint not null references usuarios (id),
    acao varchar(40) not null,
    detalhes varchar(500),
    ip varchar(45),
    ocorrida_em timestamptz not null
);
create index idx_auditoria_conta_usuario on auditoria_conta (usuario_id, ocorrida_em desc);

create table troca_email (
    id bigserial primary key,
    usuario_id bigint not null references usuarios (id),
    email_novo varchar(150) not null,
    token_hash varchar(64) not null,
    criada_em timestamptz not null,
    expira_em timestamptz not null,
    usado_em timestamptz,
    cancelada_em timestamptz,
    constraint uk_troca_email_token unique (token_hash)
);
-- No maximo uma troca pendente por usuario (nova solicitacao substitui a anterior).
create unique index uk_troca_email_pendente on troca_email (usuario_id)
    where usado_em is null and cancelada_em is null;

create table solicitacoes_exclusao (
    id bigserial primary key,
    usuario_id bigint not null references usuarios (id),
    email_no_pedido varchar(150) not null,
    estado varchar(30) not null,
    motivo varchar(500),
    sinais_risco varchar(2000),
    ip varchar(45),
    criada_em timestamptz not null,
    -- Periodo minimo de analise: a equipe so pode aprovar depois desta data.
    analise_apos timestamptz not null,
    analista_id bigint references usuarios (id),
    decidida_em timestamptz,
    observacao_interna varchar(1000),
    token_cancelamento_hash varchar(64) not null,
    constraint uk_exclusao_token unique (token_cancelamento_hash),
    constraint ck_exclusao_estado check (estado in
        ('PENDENTE', 'EM_ANALISE', 'APROVADA', 'NEGADA', 'CONCLUIDA', 'CANCELADA_PELO_USUARIO'))
);
-- Uma unica solicitacao aberta por usuario.
create unique index uk_exclusao_aberta on solicitacoes_exclusao (usuario_id)
    where estado in ('PENDENTE', 'EM_ANALISE');
create index idx_exclusao_estado on solicitacoes_exclusao (estado, criada_em);

create table solicitacao_exclusao_historico (
    id bigserial primary key,
    solicitacao_id bigint not null references solicitacoes_exclusao (id),
    estado_anterior varchar(30),
    estado_novo varchar(30) not null,
    ocorrida_em timestamptz not null,
    autor_id bigint references usuarios (id),
    observacao varchar(500)
);
create index idx_exclusao_historico on solicitacao_exclusao_historico (solicitacao_id, ocorrida_em);
