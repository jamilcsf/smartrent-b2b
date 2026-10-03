-- =====================================================================
-- SmartRent B2B — mínimo de diárias, taxa de limpeza, vínculo reserva-cliente
-- e parâmetros da política de cancelamento no snapshot da reserva.
--
-- O "limite de hóspedes" já existe (imoveis.capacidade_hospedes) e é reutilizado.
-- Todas as colunas de snapshot são gravadas uma única vez, na criação da reserva
-- (a entidade as marca como não atualizáveis).
-- =====================================================================

alter table imoveis add column minimo_diarias integer not null default 1;
alter table imoveis add column taxa_limpeza numeric(10,2) not null default 0;
alter table imoveis add constraint ck_imoveis_minimo_diarias check (minimo_diarias >= 1);
alter table imoveis add constraint ck_imoveis_taxa_limpeza check (taxa_limpeza >= 0);

alter table reservas add column cliente_id bigint;
alter table reservas add column numero_hospedes integer not null default 1;
alter table reservas add column minimo_diarias_snapshot integer not null default 1;
alter table reservas add column taxa_limpeza_snapshot numeric(10,2) not null default 0;
alter table reservas add column limite_hospedes_snapshot integer;
alter table reservas add column politica_antecedencia_horas integer not null default 48;
alter table reservas add column politica_regret_dias integer not null default 0;
alter table reservas add column politica_versao varchar(30) not null default 'PROVISORIA-1';

-- Reservas anteriores: o limite vigente é a capacidade atual do imóvel (melhor aproximação)
-- e o hóspede é vinculado ao usuário cliente de mesmo e-mail, quando existir.
update reservas r set limite_hospedes_snapshot = i.capacidade_hospedes
from imoveis i where i.id = r.imovel_id;
update reservas set limite_hospedes_snapshot = 1 where limite_hospedes_snapshot is null;
alter table reservas alter column limite_hospedes_snapshot set not null;

update reservas r set cliente_id = u.id
from usuarios u where lower(u.email) = lower(r.hospede_email);

alter table reservas add constraint fk_reserva_cliente foreign key (cliente_id) references usuarios (id);
create index idx_reservas_cliente on reservas (cliente_id);
