# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-09-21 — Consolidação na base de código de Jamil Cherem; PostgreSQL e Flyway adequados aos RNFs  

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
- [ ] Implementação das entidades JPA — parcial: `Reserva` e `SugestaoPreco` existem; faltam `Usuario` e `Imovel`
- [x] Repositório com query de validação de choque de datas
- [x] Geração do Modelo Físico a partir das entidades — ver [ADR-002](docs/ADR-002-estrategia-de-schema.md)
- [x] Versionamento do schema com Flyway (`V1__criacao_schema_inicial.sql`)
- [x] Camada de Service e Controllers REST
- [x] Pipeline GitHub Actions (Etapa 3)
- [ ] Suíte de testes unitários com Mockito — parcial: 2 casos
- [ ] Configuração do serviço de IA com tratamento de fallback — parcial: fallback existe, a chamada à Groq é um retorno fixo
- [ ] Objetos de transferência como Records (RNF08)
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

Validado contra PostgreSQL 16.15: o Flyway aplica a migration, o Hibernate aceita
o esquema e um cadastro via API persiste no banco.

Pendente de adequação: o Modelo de Dados (faltam `Usuario`, `Imovel` e o embutido
`Endereco`, e os nomes de tabela ainda divergem da Seção 6.2 do Relatório), os
objetos de transferência como Records (RNF08) e a integração real com a Groq API,
hoje um retorno fixo no código de produção (RNF01 e RNF04).
