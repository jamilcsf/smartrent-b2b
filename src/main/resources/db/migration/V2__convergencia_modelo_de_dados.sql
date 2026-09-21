-- =====================================================================
-- SmartRent B2B — convergência com o Modelo de Dados documentado
--
-- Leva o esquema da V1 ao especificado na Seção 6.2 do Relatório Técnico:
-- cria `usuarios`, `imoveis` e a tabela de coleção `imovel_comodidades`,
-- renomeia `reserva` -> `reservas` e `tb_sugestao_preco` -> `sugestoes_preco`
-- e completa as colunas que faltavam.
--
-- As linhas existentes em `reserva` e `tb_sugestao_preco` são descartadas:
-- ambas passam a exigir um imóvel cadastrado por chave estrangeira, e
-- `imoveis` nasce vazia nesta mesma migration, de modo que nenhum registro
-- anterior teria como satisfazer a restrição. Não há perda relevante — o
-- esquema da V1 nunca foi aplicado fora de ambiente de desenvolvimento.
-- =====================================================================

-- ------------------------------------------------------------ usuarios
create table usuarios (
    id bigserial not null,
    ativo boolean not null,
    data_criacao timestamp(6) not null,
    email varchar(150) not null unique,
    nome varchar(120) not null,
    papel varchar(20) not null check (papel in ('ADMIN','ANFITRIAO')),
    senha_hash varchar(60) not null,
    telefone varchar(20),
    primary key (id)
);

-- ------------------------------------------------------------- imoveis
create table imoveis (
    id bigserial not null,
    ativo boolean not null,
    capacidade_hospedes integer not null,
    data_cadastro timestamp(6) not null,
    descricao TEXT,
    bairro varchar(100) not null,
    cep varchar(9) not null,
    cidade varchar(100) not null,
    complemento varchar(100),
    estado varchar(2) not null,
    latitude numeric(10,7) not null,
    logradouro varchar(150) not null,
    longitude numeric(10,7) not null,
    numero varchar(20),
    numero_banheiros integer not null,
    numero_quartos integer not null,
    tipo_imovel varchar(20) not null check (tipo_imovel in ('APARTAMENTO','CASA','KITNET','POUSADA','CHALE','LOFT','OUTRO')),
    titulo varchar(150) not null,
    valor_diaria_base numeric(10,2) not null,
    usuario_id bigint not null,
    primary key (id)
);

create table imovel_comodidades (
    imovel_id bigint not null,
    comodidade varchar(60)
);

-- ------------------------------------------------------------ reservas
delete from reserva;
alter table reserva rename to reservas;

alter table reservas rename column valor_diaria to valor_total;
alter table reservas alter column valor_total type numeric(10,2);
alter table reservas alter column valor_total set not null;
alter table reservas alter column data_checkin set not null;
alter table reservas alter column data_checkout set not null;
alter table reservas alter column imovel_id set not null;
alter table reservas alter column status type varchar(20);
alter table reservas alter column status set not null;
alter table reservas add constraint ck_reservas_status
    check (status in ('PENDENTE','CONFIRMADA','CANCELADA','CONCLUIDA'));

alter table reservas add column hospede_nome varchar(120) not null;
alter table reservas add column hospede_email varchar(150) not null;
alter table reservas add column hospede_telefone varchar(20);
alter table reservas add column observacoes TEXT;
alter table reservas add column data_criacao timestamp(6) not null;
alter table reservas add column origem varchar(20) not null;
alter table reservas add constraint ck_reservas_origem
    check (origem in ('DIRETA','AIRBNB','BOOKING','OUTRA_OTA'));
alter table reservas add column versao bigint;

-- ----------------------------------------------------- sugestoes_preco
delete from tb_sugestao_preco;
alter table tb_sugestao_preco rename to sugestoes_preco;

alter table sugestoes_preco alter column data_referencia set not null;
alter table sugestoes_preco alter column imovel_id set not null;
alter table sugestoes_preco alter column valor_sugerido type numeric(10,2);
alter table sugestoes_preco alter column valor_sugerido set not null;
alter table sugestoes_preco alter column origem_calculo type varchar(30);
alter table sugestoes_preco alter column origem_calculo set not null;
alter table sugestoes_preco add constraint ck_sugestoes_origem_calculo
    check (origem_calculo in ('IA_GENERATIVA','FALLBACK_REGRA_NEGOCIO'));

alter table sugestoes_preco add column valor_base numeric(10,2) not null;
alter table sugestoes_preco add column percentual_ajuste numeric(5,2);
alter table sugestoes_preco add column fator_sazonalidade numeric(5,2);
alter table sugestoes_preco add column fator_microgeografia numeric(5,2);
alter table sugestoes_preco add column justificativa_ia TEXT;
alter table sugestoes_preco add column modelo_ia_utilizado varchar(80);
alter table sugestoes_preco add column data_geracao timestamp(6) not null;

-- --------------------------------------------- chaves estrangeiras e índices
alter table imoveis add constraint fk_imovel_usuario
    foreign key (usuario_id) references usuarios;
alter table imovel_comodidades add constraint fk_comodidade_imovel
    foreign key (imovel_id) references imoveis;
alter table reservas add constraint fk_reserva_imovel
    foreign key (imovel_id) references imoveis;
alter table sugestoes_preco add constraint fk_sugestao_imovel
    foreign key (imovel_id) references imoveis;

create index idx_imoveis_usuario on imoveis (usuario_id);
create index idx_imoveis_ativo on imoveis (ativo);
create index idx_reservas_imovel on reservas (imovel_id);
create index idx_reservas_periodo on reservas (imovel_id, data_checkin, data_checkout);
create index idx_sugestoes_imovel_data on sugestoes_preco (imovel_id, data_referencia);
