# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-09-21 — Modelo de dados convergido com a Seção 6.2 e API exposta por DTOs Records  

---

## 📊 Matriz de Entregas Avaliativas (Cronograma UniSENAI)

| Entrega | Foco Avaliativo | Status | Critérios Obrigatórios |
|---|---|:---:|---|
| **Etapa 1** | Pitch de Ideias e Plano de Trabalho[cite: 4] | 🟢 Concluído | Tema, ODS 8, Problema, Solução, Escopo MoSCoW[cite: 1, 4] |
| **Etapa 2** | Modelagem de Requisitos e Sistema[cite: 4] | 🟢 Concluído | RFs, RNFs, Casos de Uso, Diagramas de Atividades, DER[cite: 4] |
| **Etapa 3** | Arquitetura, Banco e CI/CD[cite: 4] | 🟡 Em Andamento | Spring Boot 3, PostgreSQL (Supabase), GitHub Actions[cite: 4] |
| **Etapa 4** | Integração Groq/Llama e Testes Mock[cite: 4] | ⚪ Pendente | `PrecificacaoIaService`, Fallback, Testes JUnit 5/Mockito[cite: 4] |
| **Etapa 5** | Frontend Chart.js e Validação em Campo[cite: 4] | ⚪ Pendente | Telas funcionais, script de 1 min, entrevista com anfitrião[cite: 4] |
| **Etapa 6** | Relatório Final e Apresentação para Banca[cite: 4] | ⚪ Pendente | PDF consolidado e slides da defesa técnica[cite: 1, 4] |

---

## 📋 Checklist de Itens Prontos vs. Próximos Passos

- [x] Definição de Tema e Contexto da Grande Florianópolis
- [x] Justificativa técnica embasada na ODS 8 (Metas 8.2, 8.3 e 8.9)[cite: 1]
- [x] Personas e Mapeamento de Dores (Anfitrião e Gestora de Pousada)
- [x] Especificação completa de Requisitos Funcionais (RF01 a RF10)
- [x] Especificação de Requisitos Não Funcionais (RNF01 a RNF08)
- [x] Diagrama de Casos de Uso e Diagramas de Atividades (Mermaid)
- [x] Modelo Conceitual e Lógico de Banco de Dados (PostgreSQL)
- [x] Script técnico para o vídeo demonstrativo de 60 segundos[cite: 4]
- [x] Inicialização do projeto Maven (Spring Boot 3, Java 17)
- [x] Implementação das entidades JPA (`Usuario`, `Imovel`, `Reserva`, `SugestaoPreco` e o embutido `Endereco`)
- [x] Repositório com query de validação de choque de datas
- [x] Geração do Modelo Físico a partir das entidades — ver [ADR-002](docs/ADR-002-estrategia-de-schema.md)
- [x] Versionamento do schema com Flyway (`V1__` e `V2__convergencia_modelo_de_dados.sql`)
- [x] Camada de Service e Controllers REST
- [x] Objetos de transferência como Records (RNF08)
- [x] Pipeline GitHub Actions (Etapa 3)
- [ ] Endpoints REST de usuário e imóvel — hoje só se cadastra por SQL, e o seletor de imóveis do front é fixo no HTML
- [ ] Integração real com a Groq via RestClient (RNF01) — o cliente devolve valor fixo, com o mock pertencendo apenas aos testes (RNF04)
- [ ] Suíte de testes unitários com Mockito — parcial: 2 casos
- [ ] Primeiro boot contra o Supabase com o schema aplicado

---

## 🔄 Estado da base de código

Em 2026-09-21 o projeto passou a usar como base a implementação desenvolvida por
Jamil Cherem, integrada a partir de `jamilcsf/smartrent-b2b` com o histórico de
autoria preservado. A base herdada trouxe camada de serviço, controladores REST,
telas estáticas, testes unitários com Mockito e esteira de integração contínua.

O trabalho em curso é adequar essa base aos requisitos desta documentação. Já
concluído:

- **RNF02** — o H2 em memória deu lugar ao PostgreSQL.
- **RNF05** — o esquema saiu de `ddl-auto=update` para migration versionada com
  Flyway, com o Hibernate apenas validando, conforme a
  [ADR-002](docs/ADR-002-estrategia-de-schema.md).
- **RNF03** — as credenciais passaram a vir de variáveis de ambiente.
- **RNF08** — a API deixou de expor entidades e passou a usar DTOs como Records.
- **Modelo de Dados** — as quatro entidades, o objeto de valor embutido, os enums e os
  relacionamentos agora correspondem à Seção 6.2 do Relatório Técnico.

Validado contra PostgreSQL 16.15: as duas migrations aplicam em sequência, o
Hibernate aceita o esquema em `validate`, e o cadastro de reservas funciona da
interface até o banco, com a recusa de sobreposição de datas.

Pendente de adequação: a integração real com a Groq API, hoje um valor fixo no
código de produção (RNF01), e os endpoints de usuário e imóvel, sem os quais o
cadastro desses registros só acontece por SQL.
