-- =====================================================================
-- SmartRent B2B — dados de demonstração
--
-- Popula o banco com um anfitrião, oito imóveis da Grande Florianópolis e
-- reservas em situações variadas, para a aplicação ter o que mostrar.
--
-- NÃO é migration: fica fora de db/migration de propósito, para que o
-- Flyway não o aplique em ambiente real. Rode à mão, uma vez, depois que a
-- aplicação tiver subido e criado o esquema:
--
--   docker exec -i smartrent-pg psql -U postgres < scripts/dados-demonstracao.sql
--
-- É idempotente: apaga o que existir antes de inserir, então pode ser
-- executado quantas vezes for preciso, inclusive para reiniciar a demo.
-- =====================================================================

delete from smartchat_denuncias;
delete from solicitacao_exclusao_historico;
delete from solicitacoes_exclusao;
delete from troca_email;
delete from auditoria_conta;
delete from smartchat_bloqueios_usuario;
delete from smartchat_mensagens;
delete from smartchat_conversa_reservas;
delete from smartchat_conversas;
delete from pagamentos;
delete from reembolsos;
delete from bloqueios_datas;
delete from notificacoes;
delete from lembretes_edicao;
delete from auditoria_anuncio;
delete from aceites_termo;
delete from historico_preco;
delete from anuncio_rascunhos;
delete from imovel_midias;
delete from sugestoes_preco;
delete from reserva_precos_diarios;
delete from reservas;
delete from imovel_comodidades;
delete from imoveis;
delete from usuarios;

-- pg_get_serial_sequence acha a sequencia pelo nome da tabela: as tabelas
-- renomeadas na migration V2 mantiveram o nome antigo da sequencia.
select setval(pg_get_serial_sequence('usuarios', 'id'), 1, false);
select setval(pg_get_serial_sequence('imoveis', 'id'), 1, false);
select setval(pg_get_serial_sequence('reservas', 'id'), 1, false);
select setval(pg_get_serial_sequence('sugestoes_preco', 'id'), 1, false);

-- Senha de todos: "senhaSegura123" (hash BCrypt).
-- ana@smartrent.dev e gestora (ANFITRIAO); cliente@smartrent.dev e usuario comum (CLIENTE);
-- admin@smartrent.dev e o administrador (ADMIN), que acessa /admin.html (painel e mapas de calor).
insert into usuarios (ativo, data_criacao, email_verificado_em, email, nome, papel, senha_hash) values
  (true, now(), now(), 'ana@smartrent.dev', 'Ana Beatriz Rocha', 'ANFITRIAO',
   '$2a$10$IGtVQ5X8CUmd3/v6eljUGeCeD6R751CNhvx7QKtwJ6G1HkD3ICA5S'),
  (true, now(), now(), 'cliente@smartrent.dev', 'Cliente de Teste', 'CLIENTE',
   '$2a$10$IGtVQ5X8CUmd3/v6eljUGeCeD6R751CNhvx7QKtwJ6G1HkD3ICA5S'),
  (true, now(), now(), 'admin@smartrent.dev', 'Administrador da Plataforma', 'ADMIN',
   '$2a$10$IGtVQ5X8CUmd3/v6eljUGeCeD6R751CNhvx7QKtwJ6G1HkD3ICA5S');

insert into imoveis
  (ativo, capacidade_hospedes, data_cadastro, bairro, cep, cidade, estado,
   latitude, logradouro, numero, longitude, numero_banheiros, numero_quartos,
   metragem_quadrada, vagas_garagem, tipo_imovel, titulo, valor_diaria_base,
   descricao, usuario_id, status, publicado_em)
values
  (true, 3, now(), 'Canasvieiras', '88054-000', 'Florianopolis', 'SC',
   -27.4280000, 'Rua das Gaivotas', '120', -48.4590000, 1, 1, 48, 1,
   'APARTAMENTO', 'Apto C120', 240.00, 'A 300 m da praia de Canasvieiras.', 1, 'PUBLICADO', now()),

  (true, 6, now(), 'Canasvieiras', '88054-100', 'Florianopolis', 'SC',
   -27.4310000, 'Avenida das Nacoes', '880', -48.4620000, 2, 2, 82, 2,
   'CASA', 'Casa 2Q com Piscina', 390.00, 'Piscina e churrasqueira.', 1, 'PUBLICADO', now()),

  (true, 2, now(), 'Ingleses', '88058-000', 'Florianopolis', 'SC',
   -27.4350000, 'Rua Dom Joao Becker', '55', -48.3960000, 1, 1, 26, 0,
   'KITNET', 'Studio com Ar-condicionado', 180.00, 'Compacto, a 250 m da praia.', 1, 'PUBLICADO', now()),

  (true, 4, now(), 'Jurere', '88053-700', 'Florianopolis', 'SC',
   -27.4390000, 'Rua dos Pescadores', '310', -48.4980000, 1, 1, 55, 1,
   'APARTAMENTO', 'Apto 1Q Mobiliado', 320.00, 'Totalmente mobiliado.', 1, 'PUBLICADO', now()),

  (true, 8, now(), 'Lagoa da Conceicao', '88062-000', 'Florianopolis', 'SC',
   -27.6000000, 'Rua das Rendeiras', '45', -48.4700000, 3, 4, 145, 2,
   'CASA', 'Casa Frente Lagoa', 720.00, 'Vista para a Lagoa da Conceicao.', 1, 'PUBLICADO', now()),

  (true, 12, now(), 'Campeche', '88063-000', 'Florianopolis', 'SC',
   -27.6780000, 'Servidao dos Coqueiros', '77', -48.4810000, 5, 6, 320, 4,
   'POUSADA', 'Pousada Mar Aberto', 980.00, 'Seis suites e area de convivio.', 1, 'PUBLICADO', now()),

  (true, 2, now(), 'Lagoa da Conceicao', '88062-100', 'Florianopolis', 'SC',
   -27.6040000, 'Rua Manoel Severino', '210', -48.4660000, 1, 1, 38, 0,
   'LOFT', 'Loft Vista Lagoa', 290.00, 'Pe-direito alto e vista aberta.', 1, 'PUBLICADO', now()),

  (true, 5, now(), 'Santo Antonio de Lisboa', '88050-000', 'Florianopolis', 'SC',
   -27.5080000, 'Rodovia SC-401', 'km 12', -48.5120000, 2, 3, 110, 2,
   'CHALE', 'Chale Acoriano', 460.00, 'Arquitetura acoriana, por do sol.', 1, 'PUBLICADO', now());

insert into imovel_comodidades (imovel_id, comodidade) values
  (1,'Wi-Fi'),(1,'Ar-condicionado'),
  (2,'Piscina'),(2,'Churrasqueira'),(2,'Wi-Fi'),
  (3,'Ar-condicionado'),
  (4,'Wi-Fi'),(4,'Mobiliado'),
  (5,'Vista para a lagoa'),(5,'Wi-Fi'),(5,'Churrasqueira'),
  (6,'Piscina'),(6,'Cafe da manha'),(6,'Estacionamento'),
  (7,'Wi-Fi'),
  (8,'Lareira'),(8,'Wi-Fi');

-- Situacoes variadas: confirmada, pendente e cancelada, em canais diferentes.
insert into reservas
  (imovel_id, hospede_nome, hospede_email, data_checkin,
   data_checkout, valor_total, status, origem, data_criacao, versao, observacoes,
   moeda, numero_diarias, preco_diaria_snapshot, taxas_snapshot, total_snapshot,
   imovel_titulo_snapshot, imovel_endereco_snapshot, imovel_caracteristicas_snapshot,
   limite_hospedes_snapshot, cliente_id)
values
  (2, 'Maria Souza',     'maria@exemplo.com',
   date '2026-10-02', date '2026-10-07', 1950.00, 'CONFIRMADA', 'AIRBNB',  now(), 0, 'Chegada apos as 20h.',
   'BRL', 1, 0, 0, 0, 'tmp', null, null, 1, null),
  (1, 'Carlos Pereira',  'carlos@exemplo.com',
   date '2026-11-10', date '2026-11-14',  960.00, 'CONFIRMADA', 'DIRETA',  now(), 0, null,
   'BRL', 1, 0, 0, 0, 'tmp', null, null, 1, null),
  (5, 'Juliana Alves',   'juliana@exemplo.com',
   date '2026-12-20', date '2026-12-27', 5040.00, 'PENDENTE',   'BOOKING', now(), 0, 'Aguardando confirmacao de pagamento.',
   'BRL', 1, 0, 0, 0, 'tmp', null, null, 1, null),
  (6, 'Ricardo Nunes',   'ricardo@exemplo.com',
   date '2027-01-05', date '2027-01-12', 6860.00, 'CONFIRMADA', 'DIRETA',  now(), 0, 'Grupo de 10 pessoas.',
   'BRL', 1, 0, 0, 0, 'tmp', null, null, 1, null),
  (3, 'Fernanda Lima',   'fernanda@exemplo.com',
   date '2026-11-02', date '2026-11-05',  540.00, 'CANCELADA_SEM_REEMBOLSO',  'OUTRA_OTA', now(), 0, 'Cancelada pelo hospede.',
   'BRL', 1, 0, 0, 0, 'tmp', null, null, 1, null);

-- Reservas do cliente de teste (cliente@smartrent.dev), com pagamento aprovado: ao rodar o job de
-- reconciliacao do SmartChat (ou ao abrir a conversa pela reserva) a conversa com a gestora aparece.
insert into reservas
  (imovel_id, hospede_nome, hospede_email, data_checkin, data_checkout, valor_total, status, origem,
   data_criacao, versao, moeda, numero_diarias, preco_diaria_snapshot, taxas_snapshot, total_snapshot,
   imovel_titulo_snapshot, limite_hospedes_snapshot, cliente_id, numero_hospedes)
values
  (3, 'Cliente de Teste', 'cliente@smartrent.dev', date '2027-02-10', date '2027-02-13', 540.00, 'CONFIRMADA',
   'DIRETA', now(), 0, 'BRL', 3, 180, 0, 540, 'tmp', 2, 2, 2);

-- Snapshot das reservas (preco e dados do imovel congelados): derivado do que ja
-- foi inserido acima, como faz a migration V6 para reservas antigas.
update reservas r set
    moeda = 'BRL',
    numero_diarias = greatest(r.data_checkout - r.data_checkin, 1),
    taxas_snapshot = 0,
    total_snapshot = r.valor_total,
    preco_diaria_snapshot = round(r.valor_total / greatest(r.data_checkout - r.data_checkin, 1), 2),
    imovel_titulo_snapshot = i.titulo,
    imovel_endereco_snapshot = concat_ws(', ', i.logradouro, i.numero, i.bairro, i.cidade || '/' || i.estado),
    imovel_caracteristicas_snapshot = concat_ws(' · ', i.metragem_quadrada || ' m²',
        i.numero_quartos || ' quarto(s)', i.numero_banheiros || ' banheiro(s)')
from imoveis i where i.id = r.imovel_id;

update reservas r set limite_hospedes_snapshot = i.capacidade_hospedes
from imoveis i where i.id = r.imovel_id;

insert into pagamentos (reserva_id, valor, moeda, status, chave_idempotencia, ref_gateway, criado_em)
select id, total_snapshot, 'BRL', 'APROVADO', 'demo:reserva:' || id, 'SBX-DEMO-' || id, now()
from reservas where cliente_id is not null and status = 'CONFIRMADA';

-- Um bloqueio de datas de exemplo (uso proprio) para o calendario do imovel 1.
insert into bloqueios_datas (imovel_id, data_inicio, data_fim, motivo, observacao, criado_por, criado_em)
values (1, date '2026-12-24', date '2026-12-26', 'USO_PROPRIO', 'Natal em familia', 1, now());

insert into reserva_precos_diarios (reserva_id, data, valor)
select r.id, d::date, r.preco_diaria_snapshot
from reservas r, generate_series(r.data_checkin, r.data_checkout - 1, interval '1 day') d;

select 'usuarios' as tabela, count(*) from usuarios
union all select 'imoveis', count(*) from imoveis
union all select 'reservas', count(*) from reservas;
