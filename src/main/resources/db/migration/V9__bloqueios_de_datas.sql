-- =====================================================================
-- SmartRent B2B — bloqueio manual de datas pelo gestor
--
-- data_inicio e data_fim são datas puras (sem hora e sem fuso) e ambas
-- inclusivas: bloquear 10 a 12 indisponibiliza as noites de 10, 11 e 12.
-- Motivo e observação são internos e nunca chegam a clientes.
-- =====================================================================

create table bloqueios_datas (
    id bigserial primary key,
    imovel_id bigint not null references imoveis (id),
    data_inicio date not null,
    data_fim date not null,
    motivo varchar(30) not null,
    observacao varchar(500),
    criado_por bigint not null,
    criado_em timestamptz not null,
    constraint ck_bloqueio_periodo check (data_fim >= data_inicio)
);
create index idx_bloqueios_imovel_periodo on bloqueios_datas (imovel_id, data_inicio, data_fim);
