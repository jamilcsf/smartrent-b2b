-- =====================================================================
-- SmartChat: analise de comportamento (envio em massa e suspeita de fraude).
-- texto_hmac guarda so o HMAC-SHA-256 do texto normalizado, para comparar
-- mensagens iguais sem guardar o texto. alertas_internos nao tem conteudo de
-- mensagem: usuario, tipo, contagem, janela observada e status.
-- =====================================================================

alter table smartchat_mensagens add column texto_hmac varchar(64);
create index idx_smartchat_msg_hmac on smartchat_mensagens (autor_id, texto_hmac, criada_em);

create table alertas_internos (
    id bigserial not null,
    usuario_id bigint not null,
    tipo varchar(30) not null,
    contagem integer not null,
    janela_inicio timestamptz not null,
    janela_fim timestamptz not null,
    status varchar(15) not null default 'ABERTO',
    criado_em timestamptz not null,
    primary key (id),
    constraint fk_alerta_usuario foreign key (usuario_id) references usuarios
);
create index idx_alertas_usuario_tipo on alertas_internos (usuario_id, tipo, criado_em);
