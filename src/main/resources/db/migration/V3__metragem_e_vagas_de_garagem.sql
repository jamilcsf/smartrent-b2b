-- =====================================================================
-- SmartRent B2B — área útil e vagas de garagem
--
-- Ambas são opcionais de propósito. Os imóveis já cadastrados não têm essa
-- informação, e exigi-la obrigaria a inventar valor para eles. Na garagem,
-- nulo ("não informado") e zero ("não tem") são estados diferentes que a
-- interface trata do mesmo jeito: não exibe.
-- =====================================================================

alter table imoveis add column metragem_quadrada integer;
alter table imoveis add column vagas_garagem integer;
