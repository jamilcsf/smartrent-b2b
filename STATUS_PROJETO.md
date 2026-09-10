# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-09-09 — Build Maven/Spring Boot inicializado e schema versionado com Flyway, validado contra PostgreSQL 16  

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
- [x] Implementação das entidades JPA (`Usuario`, `Imovel`, `Reserva`, `SugestaoPreco`)
- [x] Criação dos repositórios Spring Data JPA com query de validação de choque de datas
- [x] Inicialização do projeto Maven (Spring Boot 3.5.16, Java 17) e Maven Wrapper
- [x] `SecurityConfig` temporária liberando os endpoints até existir autenticação
- [x] Geração do Modelo Físico a partir das entidades — ver [ADR-002](docs/ADR-002-estrategia-de-schema.md)
- [x] Versionamento do schema com Flyway (`V1__criacao_schema_inicial.sql`)
- [x] Ciclo Flyway + `ddl-auto: validate` validado ponta a ponta contra PostgreSQL 16
- [ ] Primeiro boot contra o Supabase (pendente apenas das credenciais)
- [ ] Camada de Service e Controllers REST
- [ ] Configuração do serviço de IA com tratamento de fallback
- [ ] Escrita da suíte de testes unitários com Mockito
- [ ] Pipeline GitHub Actions (Etapa 3)

---

## 🔧 Como rodar

Pré-requisitos: JDK 17+ (`JAVA_HOME` apontando para ele). Maven não é
necessário — use o wrapper.

```bash
# variáveis de ambiente (ver .env.example)
export DATABASE_URL="jdbc:postgresql://<host>.supabase.co:5432/postgres?sslmode=require"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="<senha>"

./mvnw spring-boot:run
```

No primeiro boot o Flyway aplica `V1__criacao_schema_inicial.sql` e o Hibernate
valida o mapeamento contra o schema resultante.

Para regerar o DDL após alterar alguma entidade:

```bash
./mvnw -Pschema-gen process-test-classes   # escreve target/schema-postgres.sql
```

O resultado deve virar uma migration nova (`V2__...`), nunca uma edição da V1.