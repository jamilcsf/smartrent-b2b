-- =====================================================================
-- SmartRent B2B — ciclo de vida do anúncio
--
-- Acrescenta o papel CLIENTE e o status do anúncio (máquina de estados),
-- além dos carimbos de tempo das janelas de 24h (pré-publicação) e de 2h
-- (republicação). Imóveis já cadastrados nascem PUBLICADOS: eram visíveis
-- antes e continuam sendo.
-- =====================================================================

alter table usuarios drop constraint if exists usuarios_papel_check;
alter table usuarios add constraint ck_usuarios_papel
    check (papel in ('ADMIN','ANFITRIAO','CLIENTE'));

-- Sem preço e sem coordenadas o imóvel ainda pode existir: o preço só é
-- definido depois do cadastro, e a geocodificação é opcional.
alter table imoveis alter column valor_diaria_base drop not null;
alter table imoveis alter column latitude drop not null;
alter table imoveis alter column longitude drop not null;

alter table imoveis add column status varchar(30) not null default 'PUBLICADO';
alter table imoveis alter column status drop default;
alter table imoveis add constraint ck_imoveis_status check (status in (
    'PRE_PUBLICACAO_SEM_PRECO','PRE_PUBLICACAO_AGUARDANDO','PRONTO_PARA_PUBLICAR',
    'PUBLICADO','EM_EDICAO','REPUBLICACAO_AGENDADA'));

alter table imoveis add column preco_primeira_confirmacao_em timestamp(6);
alter table imoveis add column publicado_em timestamp(6);
alter table imoveis add column edicao_iniciada_em timestamp(6);
alter table imoveis add column edicao_estado_origem varchar(30);
alter table imoveis add column republicar_original_em timestamp(6);
alter table imoveis add column edicao_confirmada_em timestamp(6);
alter table imoveis add column republicar_em timestamp(6);
alter table imoveis add column whatsapp_link varchar(300);

update imoveis set publicado_em = data_cadastro;

create index idx_imoveis_status on imoveis (status);
