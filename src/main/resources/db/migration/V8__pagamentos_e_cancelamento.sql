-- =====================================================================
-- SmartRent B2B — pagamentos (gateway simulado), cancelamento e reembolso
--
-- Instantes novos usam timestamptz (UTC); datas de calendário seguem "date".
-- =====================================================================

-- Os novos estados de cancelamento (CANCELADA_SEM_REEMBOLSO...) passam de 20 caracteres.
alter table reservas alter column status type varchar(30);
alter table reservas drop constraint ck_reservas_status;
-- Reservas já canceladas antes da política: sem direito a reembolso (nunca foram pagas pela plataforma).
update reservas set status = 'CANCELADA_SEM_REEMBOLSO' where status = 'CANCELADA';
alter table reservas add constraint ck_reservas_status check (status in (
    'PENDENTE','CONFIRMADA','CANCELADA_COM_REEMBOLSO','CANCELADA_SEM_REEMBOLSO','CANCELADA_PELO_GESTOR','CONCLUIDA'));

alter table reservas add column cancelada_em timestamptz;
alter table reservas add column cancelada_por varchar(20);
alter table reservas add column cancelamento_regra varchar(40);
alter table reservas add column cancelamento_motivo varchar(300);


create table pagamentos (
    id bigserial primary key,
    reserva_id bigint not null references reservas (id),
    valor numeric(10,2) not null,
    moeda varchar(3) not null default 'BRL',
    status varchar(20) not null,
    chave_idempotencia varchar(100) not null,
    ref_gateway varchar(80),
    motivo_recusa varchar(200),
    criado_em timestamptz not null,
    constraint uk_pagamento_chave unique (chave_idempotencia)
);
create index idx_pagamentos_reserva on pagamentos (reserva_id);

create table reembolsos (
    id bigserial primary key,
    reserva_id bigint not null references reservas (id),
    valor numeric(10,2) not null,
    status varchar(20) not null,
    chave_idempotencia varchar(100) not null,
    ref_gateway varchar(80),
    tentativas integer not null default 0,
    erro varchar(300),
    proxima_tentativa_em timestamptz,
    criado_em timestamptz not null,
    atualizado_em timestamptz not null,
    constraint uk_reembolso_reserva unique (reserva_id),
    constraint uk_reembolso_chave unique (chave_idempotencia)
);
