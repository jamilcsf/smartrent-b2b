-- =====================================================================
-- SmartRent B2B — mídias, rascunho de edição, preços, aceites, auditoria,
-- lembretes e notificações do anúncio.
-- =====================================================================

create table imovel_midias (
    id bigserial not null,
    imovel_id bigint not null,
    chave varchar(36) not null unique,
    tipo varchar(10) not null check (tipo in ('FOTO','FOTO_360','VIDEO')),
    estado varchar(10) not null check (estado in ('ATIVA','NOVA','REMOVIDA')),
    arquivo varchar(120) not null,
    miniatura varchar(120),
    mime varchar(40) not null,
    tamanho_bytes bigint not null,
    largura integer,
    altura integer,
    duracao_segundos integer,
    ordem integer not null,
    capa boolean not null,
    data_envio timestamp(6) not null,
    primary key (id),
    constraint fk_midia_imovel foreign key (imovel_id) references imoveis
);
create index idx_midias_imovel on imovel_midias (imovel_id);

-- Um único rascunho por imóvel: guarda só o que o gestor mudou na edição,
-- sem tocar o anúncio que está (ou estava) no ar.
create table anuncio_rascunhos (
    id bigserial not null,
    imovel_id bigint not null unique,
    dados TEXT not null,
    midia_ordem TEXT,
    capa_midia_id bigint,
    criado_em timestamp(6) not null,
    atualizado_em timestamp(6) not null,
    primary key (id),
    constraint fk_rascunho_imovel foreign key (imovel_id) references imoveis
);

create table historico_preco (
    id bigserial not null,
    imovel_id bigint not null,
    valor_anterior numeric(10,2),
    valor_novo numeric(10,2) not null,
    autor_id bigint not null,
    origem varchar(10) not null check (origem in ('MANUAL','IA')),
    data_hora timestamp(6) not null,
    primary key (id),
    constraint fk_hist_preco_imovel foreign key (imovel_id) references imoveis,
    constraint fk_hist_preco_autor foreign key (autor_id) references usuarios
);
create index idx_hist_preco_imovel on historico_preco (imovel_id, data_hora);

create table aceites_termo (
    id bigserial not null,
    usuario_id bigint not null,
    imovel_id bigint not null,
    versao_termo varchar(20) not null,
    contexto varchar(30) not null check (contexto in ('CADASTRO','PUBLICACAO','CONFIRMACAO_EDICAO')),
    data_hora timestamp(6) not null,
    ip varchar(45),
    primary key (id),
    constraint fk_aceite_usuario foreign key (usuario_id) references usuarios,
    constraint fk_aceite_imovel foreign key (imovel_id) references imoveis
);
create index idx_aceites_imovel on aceites_termo (imovel_id);

create table auditoria_anuncio (
    id bigserial not null,
    imovel_id bigint not null,
    usuario_id bigint,
    acao varchar(40) not null,
    detalhes varchar(500),
    data_hora timestamp(6) not null,
    primary key (id),
    constraint fk_auditoria_imovel foreign key (imovel_id) references imoveis
);
create index idx_auditoria_imovel on auditoria_anuncio (imovel_id, data_hora);

-- Cada lembrete é identificado por (imóvel, edição, número, canal): a chave
-- única é o que garante que dois disparos do job nunca duplicam o envio.
create table lembretes_edicao (
    id bigserial not null,
    imovel_id bigint not null,
    edicao_iniciada_em timestamp(6) not null,
    numero integer not null,
    canal varchar(20) not null,
    criado_em timestamp(6) not null,
    enviado_em timestamp(6),
    tentativas integer not null,
    erro varchar(500),
    primary key (id),
    constraint fk_lembrete_imovel foreign key (imovel_id) references imoveis,
    constraint uk_lembrete unique (imovel_id, edicao_iniciada_em, numero, canal)
);

create table notificacoes (
    id bigserial not null,
    usuario_id bigint not null,
    imovel_id bigint,
    titulo varchar(150) not null,
    mensagem varchar(600) not null,
    link varchar(200),
    lida boolean not null,
    criada_em timestamp(6) not null,
    primary key (id),
    constraint fk_notificacao_usuario foreign key (usuario_id) references usuarios
);
create index idx_notificacoes_usuario on notificacoes (usuario_id, lida);
