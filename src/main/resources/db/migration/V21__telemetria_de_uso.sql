-- =====================================================================
-- Telemetria de uso (painel de admin, mapas de calor, ADR-009).
-- Uma linha por evento de interface (visualizacao, clique, rolagem, permanencia), ANONIMA por desenho:
-- nao ha usuario_id, IP, texto de elemento nem URL com query. O sessao_id e um sorteio do navegador (vale por aba).
-- A tabela e append-only e cresce rapido; por isso ha indices so para as consultas do painel, colunas
-- denormalizadas (dia, hora, dia_semana, celulas da grade) para agregar sem funcoes de data, e retencao em dias
-- (smartrent.telemetria.retencao-dias). Os eventos brutos podem ser exportados para analise externa (big data).
-- =====================================================================

create table eventos_uso (
    id bigserial primary key,
    tipo varchar(12) not null,          -- VISUALIZACAO | CLIQUE | ROLAGEM | PERMANENCIA
    pagina varchar(80) not null,        -- caminho normalizado, ex.: /imoveis/{id}
    alvo varchar(100),                  -- CLIQUE: #id, [data-track] ou tag[href]; nunca o texto do elemento
    sessao_id varchar(36) not null,
    papel varchar(12) not null,         -- VISITANTE | CLIENTE | ANFITRIAO | ADMIN (o papel, nao a pessoa)
    dispositivo varchar(8) not null,    -- MOBILE | TABLET | DESKTOP
    x_pm integer,                       -- CLIQUE: posicao horizontal em milesimos da largura do documento
    y_px integer,                       -- CLIQUE: posicao vertical em pixels desde o topo do documento
    doc_altura integer,                 -- CLIQUE/VISUALIZACAO: altura do documento (para escalar o mapa)
    x_celula integer,                   -- CLIQUE: coluna da grade (0..49) = x_pm / 20
    y_celula integer,                   -- CLIQUE: linha da grade = y_px / 25
    valor integer,                      -- ROLAGEM: profundidade maxima (%), PERMANENCIA: segundos
    dia date not null,                  -- no fuso da plataforma
    hora integer not null,              -- 0..23, no fuso da plataforma
    dia_semana integer not null,        -- 1 (segunda) .. 7 (domingo), ISO
    ocorrido_em timestamptz not null
);

create index idx_eventos_uso_pagina on eventos_uso (pagina, tipo, dia);
create index idx_eventos_uso_dia on eventos_uso (dia, tipo);
create index idx_eventos_uso_ocorrido on eventos_uso (ocorrido_em);
