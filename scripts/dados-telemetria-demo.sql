-- =====================================================================
-- SmartRent B2B — eventos de uso SINTETICOS para demonstrar o painel de admin (mapas de calor, uso do site)
--
-- NAO e migration (fica fora de db/migration de proposito) e NAO tem dados reais: gera ~2.400 sessoes ficticias
-- dos ultimos 30 dias, com identificadores "demo-NNNNNN". Os cliques so existem para DESKTOP, em torno dos
-- elementos reais do catalogo (/imoveis.html) e do detalhe (/imoveis/{id}) medidos a 1280 px de largura; celular e
-- tablet geram so visualizacao, rolagem e permanencia (o layout deles e outro). Tendencias e picos de horario sao
-- inventados.
--
-- Rode depois que a aplicacao tiver subido (a migration V21 cria a tabela):
--
--   docker exec -i smartrent-pg psql -U postgres -d smartrent_demo < scripts/dados-telemetria-demo.sql
--
-- Idempotente: apaga so as linhas "demo-%" antes de inserir; eventos coletados de verdade nao sao tocados.
-- =====================================================================

begin;

delete from eventos_uso where sessao_id like 'demo-%';

create temp table pontos_quentes (pagina text, alvo text, x int, y int, altura int, peso int) on commit drop;
insert into pontos_quentes values
  -- catalogo (altura 1111)
  ('/imoveis.html', '#filtroLocal',            123,  277, 1111, 12),
  ('/imoveis.html', 'button',                   57,  409, 1111,  6),
  ('/imoveis.html', 'button',                  123,  409, 1111,  8),
  ('/imoveis.html', 'button',                  190,  470, 1111,  5),
  ('/imoveis.html', '#sliderMax',              123,  629, 1111,  4),
  ('/imoveis.html', '#precoMax',               174,  690, 1111,  3),
  ('/imoveis.html', 'button',                  209,  787, 1111,  6),
  ('/imoveis.html', 'button',                  209,  827, 1111,  3),
  ('/imoveis.html', '#btnLimparFiltros',       207,  192, 1111,  3),
  ('/imoveis.html', 'a[href=/imoveis/{id}]',   380,  330, 1111, 18),
  ('/imoveis.html', 'a[href=/imoveis/{id}]',   620,  330, 1111, 24),
  ('/imoveis.html', 'a[href=/imoveis/{id}]',   860,  330, 1111, 14),
  ('/imoveis.html', 'a[href=/imoveis/{id}]',   620,  600, 1111, 10),
  ('/imoveis.html', 'a[href=/imoveis/{id}]',   380,  600, 1111,  6),
  ('/imoveis.html', 'a[href=/login.html]',     897,   32, 1111,  9),
  ('/imoveis.html', 'a[href=/cadastro.html]',  953,   32, 1111,  7),
  -- detalhe do anuncio (altura 1120)
  ('/imoveis/{id}', '#btnReservarCliente',     832,  883, 1120, 26),
  ('/imoveis/{id}', '#dataCheckin',            768,  736, 1120, 16),
  ('/imoveis/{id}', '#dataCheckout',           897,  736, 1120, 12),
  ('/imoveis/{id}', '#numHospedes',            832,  807, 1120,  7),
  ('/imoveis/{id}', '#btnChat',                832,  936, 1120, 10),
  ('/imoveis/{id}', 'button',                  915,  649, 1120,  3),
  ('/imoveis/{id}', 'img',                     400,  250, 1120, 12),
  ('/imoveis/{id}', 'a[href=/imoveis.html]',    73,  100, 1120,  5);

create temp table pontos_expandidos on commit drop as
  select p.* from pontos_quentes p cross join lateral generate_series(1, p.peso);

-- Sessoes: mais visitas nos dias recentes, picos as 10h e as 20h.
create temp table sessoes on commit drop as
  select g as i,
         'demo-' || lpad(g::text, 6, '0') as sessao,
         (current_date - floor(30 * power(random(), 1.35))::int) as dia,
         case when random() < 0.25 then least(23, greatest(0, round(10 + (random() + random() + random() - 1.5) * 4)::int))
              else least(23, greatest(0, round(20 + (random() + random() + random() - 1.5) * 4)::int)) end as hora,
         floor(random() * 3300)::int as segundo,
         case when r1 < 0.50 then 'DESKTOP' when r1 < 0.90 then 'MOBILE' else 'TABLET' end as dispositivo,
         case when r2 < 0.70 then 'VISITANTE' when r2 < 0.92 then 'CLIENTE' else 'ANFITRIAO' end as papel,
         random() as r_detalhe, random() as r_login
  from (select g, random() as r1, random() as r2 from generate_series(1, 2400) g) x;

alter table sessoes add column ts timestamptz;
update sessoes set ts = ((dia + hora * interval '1 hour' + segundo * interval '1 second') at time zone 'America/Sao_Paulo');

-- Paginas visitadas por sessao: todo mundo passa pelo catalogo; 70% abre um anuncio; 10% vai ao login.
create temp table visitas on commit drop as
  select s.*, '/imoveis.html'::text as pagina, 1111 as altura, 0 as atraso from sessoes s
  union all select s.*, '/imoveis/{id}', 1120, 40 from sessoes s where s.r_detalhe < 0.70
  union all select s.*, '/login.html', 760, 120 from sessoes s where s.r_login < 0.10;

insert into eventos_uso (tipo, pagina, alvo, sessao_id, papel, dispositivo, x_pm, y_px, doc_altura, x_celula, y_celula, valor,
                         dia, hora, dia_semana, ocorrido_em)
-- visualizacao
select 'VISUALIZACAO', v.pagina, null::text, v.sessao, v.papel, v.dispositivo, null::int, null::int, v.altura, null::int, null::int, null::int,
       v.dia, v.hora, extract(isodow from v.dia)::int, v.ts + v.atraso * interval '1 second'
from visitas v
union all
-- rolagem: a maioria nao passa da dobra
select 'ROLAGEM', v.pagina, null, v.sessao, v.papel, v.dispositivo, null, null, null, null, null,
       10 * (1 + floor(power(random(), 1.6) * 10))::int,
       v.dia, v.hora, extract(isodow from v.dia)::int, v.ts + (v.atraso + 20) * interval '1 second'
from visitas v
union all
-- permanencia (segundos): cauda longa
select 'PERMANENCIA', v.pagina, null, v.sessao, v.papel, v.dispositivo, null, null, null, null, null,
       least(1800, 6 + floor(-ln(1 - random()) * 38))::int,
       v.dia, v.hora, extract(isodow from v.dia)::int, v.ts + (v.atraso + 30) * interval '1 second'
from visitas v;

-- cliques (so desktop), em torno dos pontos quentes
insert into eventos_uso (tipo, pagina, alvo, sessao_id, papel, dispositivo, x_pm, y_px, doc_altura, x_celula, y_celula, valor,
                         dia, hora, dia_semana, ocorrido_em)
select 'CLIQUE', v.pagina, h.alvo, v.sessao, v.papel, 'DESKTOP',
       least(1000, greatest(0, h.x + round((random() - 0.5) * 22 + (random() - 0.5) * 10)))::int,
       greatest(0, h.y + round((random() - 0.5) * 28))::int,
       h.altura, null, null, null,
       v.dia, v.hora, extract(isodow from v.dia)::int, v.ts + (v.atraso + 5 + k * 4) * interval '1 second'
from visitas v
cross join lateral generate_series(1, case when v.pagina = '/imoveis.html' then 1 + floor(random() * 4)::int else floor(random() * 3)::int end) k
cross join lateral (select * from pontos_expandidos e where e.pagina = v.pagina and k > 0 order by random() limit 1) h
where v.dispositivo = 'DESKTOP' and v.pagina <> '/login.html';

update eventos_uso set x_celula = x_pm / 20, y_celula = y_px / 25
where sessao_id like 'demo-%' and tipo = 'CLIQUE';

commit;

select tipo, count(*) as eventos, count(distinct sessao_id) as sessoes from eventos_uso where sessao_id like 'demo-%' group by tipo order by tipo;
