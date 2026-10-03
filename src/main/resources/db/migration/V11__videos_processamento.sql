-- =====================================================================
-- SmartRent B2B — vídeos do anúncio: envio em partes, processamento assíncrono
--
-- Limite de 1:30 por vídeo (regra do código, validada no servidor). Cada vídeo
-- passa por ENVIANDO -> PROCESSANDO -> PRONTO (ou FALHA, com motivo). As
-- colunas de envio (tamanho_total/bytes_recebidos) permitem retomar um envio
-- interrompido e limpar envios abandonados.
-- =====================================================================

alter table imovel_midias add column status_processamento varchar(15);
alter table imovel_midias add column motivo_falha varchar(300);
alter table imovel_midias add column poster varchar(160);
alter table imovel_midias add column hls_mestre varchar(160);
alter table imovel_midias add column original_arquivo varchar(160);
alter table imovel_midias add column tentativas integer not null default 0;
alter table imovel_midias add column proxima_tentativa_em timestamptz;
alter table imovel_midias add column tamanho_total bigint;
alter table imovel_midias add column bytes_recebidos bigint;
alter table imovel_midias add column atualizado_em timestamptz;
alter table imovel_midias alter column arquivo type varchar(160);

-- Vídeos já existentes foram validados no envio antigo: ficam prontos.
update imovel_midias set status_processamento = 'PRONTO' where tipo = 'VIDEO';

alter table imovel_midias add constraint ck_midia_status_processamento
    check (status_processamento is null or status_processamento in ('ENVIANDO', 'PROCESSANDO', 'PRONTO', 'FALHA'));
create index idx_midias_processamento on imovel_midias (status_processamento, proxima_tentativa_em);
