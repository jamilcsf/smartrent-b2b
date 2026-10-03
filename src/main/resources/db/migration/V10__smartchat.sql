-- =====================================================================
-- SmartRent B2B — SmartChat (canal único entre cliente e gestor),
-- denúncias e bloqueios de usuário (protótipo, sem efeito funcional).
--
-- Conversa única por cliente + gestor + imóvel (restrição no banco).
-- Mensagem de sistema tem chave de idempotência única: retry de webhook,
-- confirmação repetida ou concorrência nunca duplicam.
-- texto_original é restrito ao backend: nenhum endpoint de usuário o expõe.
-- =====================================================================

alter table usuarios add column smartchat_liberado boolean not null default false;

create table smartchat_conversas (
    id bigserial primary key,
    cliente_id bigint not null references usuarios (id),
    gestor_id bigint not null references usuarios (id),
    imovel_id bigint not null references imoveis (id),
    criada_em timestamptz not null,
    ultima_mensagem_em timestamptz,
    constraint uk_smartchat_conversa unique (cliente_id, gestor_id, imovel_id)
);
create index idx_smartchat_conversa_gestor on smartchat_conversas (gestor_id, ultima_mensagem_em desc);
create index idx_smartchat_conversa_cliente on smartchat_conversas (cliente_id, ultima_mensagem_em desc);

create table smartchat_conversa_reservas (
    conversa_id bigint not null references smartchat_conversas (id),
    reserva_id bigint not null references reservas (id),
    primary key (conversa_id, reserva_id)
);

create table smartchat_mensagens (
    id bigserial primary key,
    conversa_id bigint not null references smartchat_conversas (id),
    autor_id bigint references usuarios (id),
    tipo varchar(10) not null,
    texto_filtrado varchar(4000) not null,
    texto_original varchar(2000),
    categorias varchar(200),
    ocorrencias integer not null default 0,
    chave_idempotencia varchar(100),
    criada_em timestamptz not null,
    lida_em timestamptz,
    constraint ck_smartchat_msg_tipo check (tipo in ('NORMAL', 'SISTEMA')),
    constraint uk_smartchat_msg_chave unique (chave_idempotencia)
);
create index idx_smartchat_msg_conversa on smartchat_mensagens (conversa_id, id);

-- Prontas para o futuro módulo de administração; nada consome estas tabelas ainda.
create table smartchat_denuncias (
    id bigserial primary key,
    conversa_id bigint not null references smartchat_conversas (id),
    denunciante_id bigint not null references usuarios (id),
    denunciado_id bigint not null references usuarios (id),
    motivo varchar(40) not null,
    descricao varchar(1000),
    mensagens_anexadas varchar(500),
    status varchar(20) not null default 'PENDENTE',
    criada_em timestamptz not null
);
create index idx_smartchat_denuncia_status on smartchat_denuncias (status, criada_em);

create table smartchat_bloqueios_usuario (
    id bigserial primary key,
    conversa_id bigint references smartchat_conversas (id),
    bloqueador_id bigint not null references usuarios (id),
    bloqueado_id bigint not null references usuarios (id),
    status varchar(20) not null default 'REGISTRADO',
    criado_em timestamptz not null
);
create index idx_smartchat_bloqueio_par on smartchat_bloqueios_usuario (bloqueador_id, bloqueado_id);
