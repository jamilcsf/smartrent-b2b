-- =====================================================================
-- SmartRent B2B — fim do WhatsApp: arquiva os dados e avisa os gestores
--
-- O código deixou de ler e gravar imoveis.whatsapp_link e reservas.hospede_telefone
-- (o contato agora é só pelo SmartChat). Antes de descartar as colunas (V13), os
-- valores são arquivados em tabelas próprias, o que permite desfazer a remoção.
-- =====================================================================

create table whatsapp_arquivo (
    imovel_id bigint primary key,
    whatsapp_link varchar(300) not null,
    arquivado_em timestamp not null default (now() at time zone 'America/Sao_Paulo')
);
insert into whatsapp_arquivo (imovel_id, whatsapp_link)
select id, whatsapp_link from imoveis where whatsapp_link is not null and whatsapp_link <> '';

create table reservas_telefone_arquivo (
    reserva_id bigint primary key,
    hospede_telefone varchar(20) not null,
    arquivado_em timestamp not null default (now() at time zone 'America/Sao_Paulo')
);
insert into reservas_telefone_arquivo (reserva_id, hospede_telefone)
select id, hospede_telefone from reservas where hospede_telefone is not null and hospede_telefone <> '';

-- Aviso in-app aos gestores que tinham o link em anúncios no ar (ou em edição).
insert into notificacoes (usuario_id, imovel_id, titulo, mensagem, link, lida, criada_em)
select i.usuario_id, i.id,
       'O contato agora é pelo SmartChat',
       'O link de WhatsApp do anúncio "' || left(i.titulo, 80) || '" foi removido. Os clientes agora falam com você pelo SmartChat, dentro da plataforma.',
       '/smartchat.html', false, now() at time zone 'America/Sao_Paulo'
from imoveis i
where i.whatsapp_link is not null and i.whatsapp_link <> ''
  and i.status in ('PUBLICADO', 'EM_EDICAO', 'REPUBLICACAO_AGENDADA');
