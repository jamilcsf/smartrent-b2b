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

delete from sugestoes_preco;
delete from reservas;
delete from imovel_comodidades;
delete from imoveis;
delete from usuarios;

alter sequence usuarios_id_seq restart with 1;
alter sequence imoveis_id_seq restart with 1;
alter sequence reservas_id_seq restart with 1;
alter sequence sugestoes_preco_id_seq restart with 1;

-- Senha de todos: "senhaSegura123" (hash BCrypt).
insert into usuarios (ativo, data_criacao, email, nome, papel, senha_hash) values
  (true, now(), 'ana@smartrent.dev', 'Ana Beatriz Rocha', 'ANFITRIAO',
   '$2a$10$IGtVQ5X8CUmd3/v6eljUGeCeD6R751CNhvx7QKtwJ6G1HkD3ICA5S');

insert into imoveis
  (ativo, capacidade_hospedes, data_cadastro, bairro, cep, cidade, estado,
   latitude, logradouro, numero, longitude, numero_banheiros, numero_quartos,
   metragem_quadrada, vagas_garagem, tipo_imovel, titulo, valor_diaria_base,
   descricao, usuario_id)
values
  (true, 3, now(), 'Canasvieiras', '88054-000', 'Florianopolis', 'SC',
   -27.4280000, 'Rua das Gaivotas', '120', -48.4590000, 1, 1, 48, 1,
   'APARTAMENTO', 'Apto C120', 240.00, 'A 300 m da praia de Canasvieiras.', 1),

  (true, 6, now(), 'Canasvieiras', '88054-100', 'Florianopolis', 'SC',
   -27.4310000, 'Avenida das Nacoes', '880', -48.4620000, 2, 2, 82, 2,
   'CASA', 'Casa 2Q com Piscina', 390.00, 'Piscina e churrasqueira.', 1),

  (true, 2, now(), 'Ingleses', '88058-000', 'Florianopolis', 'SC',
   -27.4350000, 'Rua Dom Joao Becker', '55', -48.3960000, 1, 1, 26, 0,
   'KITNET', 'Studio com Ar-condicionado', 180.00, 'Compacto, a 250 m da praia.', 1),

  (true, 4, now(), 'Jurere', '88053-700', 'Florianopolis', 'SC',
   -27.4390000, 'Rua dos Pescadores', '310', -48.4980000, 1, 1, 55, 1,
   'APARTAMENTO', 'Apto 1Q Mobiliado', 320.00, 'Totalmente mobiliado.', 1),

  (true, 8, now(), 'Lagoa da Conceicao', '88062-000', 'Florianopolis', 'SC',
   -27.6000000, 'Rua das Rendeiras', '45', -48.4700000, 3, 4, 145, 2,
   'CASA', 'Casa Frente Lagoa', 720.00, 'Vista para a Lagoa da Conceicao.', 1),

  (true, 12, now(), 'Campeche', '88063-000', 'Florianopolis', 'SC',
   -27.6780000, 'Servidao dos Coqueiros', '77', -48.4810000, 5, 6, 320, 4,
   'POUSADA', 'Pousada Mar Aberto', 980.00, 'Seis suites e area de convivio.', 1),

  (true, 2, now(), 'Lagoa da Conceicao', '88062-100', 'Florianopolis', 'SC',
   -27.6040000, 'Rua Manoel Severino', '210', -48.4660000, 1, 1, 38, 0,
   'LOFT', 'Loft Vista Lagoa', 290.00, 'Pe-direito alto e vista aberta.', 1),

  (true, 5, now(), 'Santo Antonio de Lisboa', '88050-000', 'Florianopolis', 'SC',
   -27.5080000, 'Rodovia SC-401', 'km 12', -48.5120000, 2, 3, 110, 2,
   'CHALE', 'Chale Acoriano', 460.00, 'Arquitetura acoriana, por do sol.', 1);

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
  (imovel_id, hospede_nome, hospede_email, hospede_telefone, data_checkin,
   data_checkout, valor_total, status, origem, data_criacao, versao, observacoes)
values
  (2, 'Maria Souza',     'maria@exemplo.com',   '(48) 99871-2200',
   date '2026-10-02', date '2026-10-07', 1950.00, 'CONFIRMADA', 'AIRBNB',  now(), 0, 'Chegada apos as 20h.'),
  (1, 'Carlos Pereira',  'carlos@exemplo.com',  '(48) 99610-4471',
   date '2026-11-10', date '2026-11-14',  960.00, 'CONFIRMADA', 'DIRETA',  now(), 0, null),
  (5, 'Juliana Alves',   'juliana@exemplo.com', '(47) 99145-8032',
   date '2026-12-20', date '2026-12-27', 5040.00, 'PENDENTE',   'BOOKING', now(), 0, 'Aguardando confirmacao de pagamento.'),
  (6, 'Ricardo Nunes',   'ricardo@exemplo.com', null,
   date '2027-01-05', date '2027-01-12', 6860.00, 'CONFIRMADA', 'DIRETA',  now(), 0, 'Grupo de 10 pessoas.'),
  (3, 'Fernanda Lima',   'fernanda@exemplo.com', '(48) 98822-1190',
   date '2026-11-02', date '2026-11-05',  540.00, 'CANCELADA',  'OUTRA_OTA', now(), 0, 'Cancelada pelo hospede.');

select 'usuarios' as tabela, count(*) from usuarios
union all select 'imoveis', count(*) from imoveis
union all select 'reservas', count(*) from reservas;
