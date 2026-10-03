-- =====================================================================
-- SmartRent B2B — snapshot imutável das condições da reserva
--
-- A reserva passa a carregar o preço e os dados do imóvel como eram quando
-- ela foi criada. Alterar o preço ou editar o anúncio depois nunca muda o
-- que o cliente reservou nem o que ele paga.
--
-- Reservas anteriores a esta migration recebem o snapshot reconstruído a
-- partir do que já guardam (valor_total e datas) e dos dados atuais do
-- imóvel, que é a melhor aproximação disponível.
-- =====================================================================

alter table reservas add column moeda varchar(3);
alter table reservas add column numero_diarias integer;
alter table reservas add column preco_diaria_snapshot numeric(10,2);
alter table reservas add column taxas_snapshot numeric(10,2);
alter table reservas add column total_snapshot numeric(10,2);
alter table reservas add column imovel_titulo_snapshot varchar(150);
alter table reservas add column imovel_endereco_snapshot varchar(400);
alter table reservas add column imovel_caracteristicas_snapshot varchar(500);

update reservas r set
    moeda = 'BRL',
    numero_diarias = greatest(r.data_checkout - r.data_checkin, 1),
    taxas_snapshot = 0,
    total_snapshot = r.valor_total,
    preco_diaria_snapshot = round(r.valor_total / greatest(r.data_checkout - r.data_checkin, 1), 2),
    imovel_titulo_snapshot = i.titulo,
    imovel_endereco_snapshot = left(concat_ws(', ', i.logradouro, i.numero, i.bairro,
                                              i.cidade || '/' || i.estado), 400),
    imovel_caracteristicas_snapshot = left(concat_ws(' · ',
        case when i.metragem_quadrada is not null then i.metragem_quadrada || ' m²' end,
        i.numero_quartos || ' quarto(s)',
        i.numero_banheiros || ' banheiro(s)',
        case when i.vagas_garagem is not null and i.vagas_garagem > 0
             then i.vagas_garagem || ' vaga(s)' end,
        'até ' || i.capacidade_hospedes || ' hóspedes'), 500)
from imoveis i
where i.id = r.imovel_id;

alter table reservas alter column moeda set not null;
alter table reservas alter column numero_diarias set not null;
alter table reservas alter column preco_diaria_snapshot set not null;
alter table reservas alter column taxas_snapshot set not null;
alter table reservas alter column total_snapshot set not null;
alter table reservas alter column imovel_titulo_snapshot set not null;

create table reserva_precos_diarios (
    reserva_id bigint not null,
    data date not null,
    valor numeric(10,2) not null,
    primary key (reserva_id, data),
    constraint fk_preco_diario_reserva foreign key (reserva_id) references reservas
);

insert into reserva_precos_diarios (reserva_id, data, valor)
select r.id, d::date, r.preco_diaria_snapshot
from reservas r,
     generate_series(r.data_checkin, r.data_checkout - 1, interval '1 day') d;
