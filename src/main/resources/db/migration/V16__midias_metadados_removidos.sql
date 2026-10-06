-- =====================================================================
-- Fotos de anuncio passam a ser recodificadas no servidor, sem EXIF/GPS.
-- A coluna marca o que ja foi reprocessado: as fotos antigas ficam false e
-- o job SaneamentoFotosAnuncio as processa uma unica vez (idempotente).
-- =====================================================================

alter table imovel_midias add column metadados_removidos boolean not null default false;
