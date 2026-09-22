# ADR-002 — Estratégia de geração e versionamento do schema

**Status:** Aceita — diretriz vigente; a implementação correspondente foi removida
em 2026-09-16 com a reinicialização da base de código (ver seção final)
**Data:** 2026-09-09
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

As entidades JPA (`Usuario`, `Imovel`, `Reserva`, `SugestaoPreco` e o embutido
`Endereco`) já existiam antes de o projeto Maven ser inicializado. Com a
aplicação configurada em `spring.jpa.hibernate.ddl-auto: validate`, o Hibernate
não cria nem altera tabelas — ele exige que o schema já exista no PostgreSQL
(Supabase) e que bata exatamente com o mapeamento. Restavam duas perguntas:

1. **Como produzir o DDL** sem transcrevê-lo à mão a partir das entidades, o que
   é trabalhoso e diverge silenciosamente do mapeamento a cada alteração.
2. **Como aplicá-lo** de forma reproduzível, em vez de colar SQL no editor do
   Supabase — o que não deixa histórico, não roda na máquina de outro integrante
   e não serve como artefato de entrega.

## Decisão

**Geração:** o DDL é produzido pelo próprio Hibernate em modo *scripts-only*,
pela classe `SchemaGenerator` (`src/test/java/com/smartrentb2b/tools/`),
executada por `./mvnw -Pschema-gen process-test-classes`. Ela informa
`PostgreSQLDialect` explicitamente, desliga o acesso a metadados JDBC
(`hibernate.boot.allow_jdbc_metadata_access=false`) e escreve em
`target/schema-postgres.sql` via
`jakarta.persistence.schema-generation.scripts.action=create`. **Nenhum banco
participa do processo** — nem real, nem descartável.

**Versionamento:** o SQL gerado vive em
`src/main/resources/db/migration/V1__criacao_schema_inicial.sql` e é aplicado
pelo **Flyway** (`flyway-core` + `flyway-database-postgresql`) no boot, antes de
o Hibernate validar. `ddl-auto` permanece em `validate`.

## Alternativas consideradas

**Gerar contra um banco descartável (H2).** Rejeitada: o DDL sairia com a
sintaxe e os tipos do H2, e divergiria do PostgreSQL real do Supabase
justamente nos pontos que importam (`identity`, `numeric`, `TEXT`, `check`
constraints de enum). O modo scripts-only elimina a classe inteira de erro.

**`ddl-auto: update`.** Rejeitada: aplica alterações inferidas sem revisão nem
histórico, nunca remove nada, e não é aceitável em ambiente compartilhado.

**SQL manual no editor do Supabase.** Rejeitada: não fica versionado, não é
reproduzível em outra máquina e não serve como artefato do Modelo Físico.

**Hibernate `SchemaExport`.** Inviável: a classe foi **removida** no Hibernate
6.6 (versão trazida pelo Spring Boot 3.5.16). A API vigente é
`SchemaManagementToolCoordinator.process(...)`, que é a usada.

## Consequências

- O Modelo Físico exigido pelo relatório da disciplina passa a ser um artefato
  real e versionado (`V1__criacao_schema_inicial.sql`), derivado do código e não
  de um desenho paralelo que envelhece.
- Qualquer integrante sobe o banco do zero apenas com as variáveis de ambiente
  configuradas: o Flyway aplica a V1 no primeiro boot.
- `SchemaGenerator` precisa usar **as mesmas** estratégias de nomenclatura que o
  Spring Boot aplica em runtime — `CamelCaseToUnderscoresNamingStrategy`
  (física) e `SpringImplicitNamingStrategy` (implícita). Divergir aqui faria o
  `validate` reprovar o schema gerado pelo próprio projeto.
- Migrations aplicadas passam a ser **imutáveis**: alteração de mapeamento vira
  um `V2__`, nunca edição da V1, sob pena de quebrar o checksum do Flyway em
  quem já rodou.
- `baseline-on-migrate` fica **desligado**. Ligado, num schema não-vazio o
  Flyway marcaria a V1 como já aplicada e a pularia, deixando o banco sem as
  tabelas e o `validate` falhando logo em seguida.

## Notas de implementação

- A FK da tabela de `@ElementCollection` saiu do gerador com nome aleatório
  (`FKbkknv26qcsx2o2xt7rr7i5ny`) e foi renomeada para `fk_comodidade_imovel` na
  migration. O `validate` do Hibernate não confere nomes de FK, então a troca é
  segura e deixa o artefato legível.
- `imovel_comodidades` não tem chave primária nem índice em `imovel_id` — é o
  padrão do Hibernate para `@ElementCollection`. Um índice ali é candidato a
  `V2__`, caso a listagem de comodidades vire caminho quente.
- Sem conexão, o Hibernate assume PostgreSQL 12.0 como versão mínima ao escolher
  a sintaxe. O Supabase roda versão superior, então o DDL é compatível; se algum
  recurso de versão mais nova for necessário, informar
  `hibernate.dialect.version` no gerador.

## Validação

Ciclo verificado de ponta a ponta em 2026-09-09 contra PostgreSQL 16.15 em
container descartável: o Flyway aplicou a V1 (`Successfully applied 1 migration
to schema "public"`), o Hibernate aceitou o schema com `ddl-auto: validate` e a
aplicação subiu. Num segundo boot o Flyway reportou `Schema "public" is up to
date` e a validação passou novamente, confirmando que a migration é idempotente
e que o DDL gerado offline corresponde exatamente ao mapeamento.

## Situação em 2026-09-16

A base de código do projeto foi reiniciada e, com ela, saíram do repositório o
`SchemaGenerator`, o `V1__criacao_schema_inicial.sql` e a configuração do Flyway.
**A decisão registrada aqui não foi revertida:** ela permanece como diretriz a ser
seguida quando a implementação for refeita — gerar o DDL a partir das entidades em
modo scripts-only, versioná-lo como migration do Flyway e manter o Hibernate em
`validate`.

O relato de validação acima descreve uma execução que de fato ocorreu e cujo
resultado sustenta a decisão; ele é mantido como registro histórico, não como
descrição do estado atual do repositório.
