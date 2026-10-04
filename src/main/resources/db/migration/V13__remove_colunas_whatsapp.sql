-- =====================================================================
-- SmartRent B2B — descarta as colunas de WhatsApp
--
-- Passo destrutivo, separado de V12 de propósito: V12 já guardou os dados em
-- whatsapp_arquivo e reservas_telefone_arquivo. Em ambientes que exigem janela de
-- rollback, aplique primeiro o código desta versão (que não usa mais as colunas),
-- confira e só depois libere esta migration.
-- =====================================================================

alter table imoveis drop column whatsapp_link;
alter table reservas drop column hospede_telefone;
