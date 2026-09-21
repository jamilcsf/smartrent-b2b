-- =====================================================================
-- SmartRent B2B — schema inicial
--
-- Gerado a partir das entidades JPA em modo scripts-only do Hibernate,
-- com dialeto PostgreSQL e sem conexão com banco, conforme a
-- docs/ADR-002-estrategia-de-schema.md.
--
-- ATENÇÃO: este script reflete as entidades COMO ESTÃO HOJE. Os nomes
-- `reserva` e `tb_sugestao_preco` ainda divergem do Modelo de Dados
-- documentado (Seção 6.2 do Relatório Técnico), que especifica
-- `reservas` e `sugestoes_preco`, e as entidades `usuarios` e `imoveis`
-- ainda não existem. Essa convergência é trabalho à parte e virá como
-- migration nova (V2__...), nunca como edição desta.
--
-- Migrations aplicadas NUNCA devem ser editadas: alterá-las quebra o
-- checksum do Flyway em quem já as executou.
-- =====================================================================

create table reserva (
    id bigserial not null,
    data_checkin date,
    data_checkout date,
    imovel_id bigint,
    status varchar(255),
    valor_diaria numeric(38,2),
    primary key (id)
);

create table tb_sugestao_preco (
    id bigserial not null,
    data_referencia date,
    imovel_id bigint,
    origem_calculo varchar(255),
    valor_sugerido numeric(38,2),
    primary key (id)
);
