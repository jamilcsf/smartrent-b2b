# 🧭 Status Executivo e Tracking do Projeto Aplicado

**Projeto:** SmartRent B2B — Sistema de Gestão Inteligente para Aluguel por Temporada  
**Instituição:** UniSENAI — ADS (Florianópolis/SC)[cite: 1, 4]  
**Última Atualização:** 2026-09-16 — Base de código reiniciada; documentação e decisões preservadas  

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
- [ ] Inicialização do projeto Maven (Spring Boot 3, Java 17) e Maven Wrapper
- [ ] Implementação das entidades JPA (`Usuario`, `Imovel`, `Reserva`, `SugestaoPreco`)
- [ ] Criação dos repositórios Spring Data JPA com query de validação de choque de datas
- [ ] `SecurityConfig` temporária liberando os endpoints até existir autenticação
- [ ] Geração do Modelo Físico a partir das entidades — ver [ADR-002](docs/ADR-002-estrategia-de-schema.md)
- [ ] Versionamento do schema com Flyway (`V1__criacao_schema_inicial.sql`)
- [ ] Primeiro boot contra o Supabase com o schema aplicado
- [ ] Camada de Service e Controllers REST
- [ ] Configuração do serviço de IA com tratamento de fallback
- [ ] Escrita da suíte de testes unitários com Mockito
- [ ] Pipeline GitHub Actions (Etapa 3)

---

## 🔄 Estado da base de código

Em 2026-09-16 a base de código foi **reiniciada**: backend, frontend e arquivos de
configuração foram removidos para que a implementação recomece do princípio. O
repositório contém, neste momento, apenas documentação.

O que foi preservado e continua valendo como ponto de partida:

- A documentação acadêmica em `docs/relatorios-fonte/` (scripts e os 6 PDFs).
- A [ADR-002](docs/ADR-002-estrategia-de-schema.md), cuja decisão sobre geração e
  versionamento de schema permanece como diretriz para a nova implementação.
- O PRD, o `DESIGN.md` e as capturas dos protótipos em
  `stitch_smartrent_b2b_precifica_o_ia/`.

O código anterior não foi perdido: permanece recuperável no histórico do Git, no
commit `5385be3` e anteriores.

As instruções de execução voltarão a esta seção quando o novo projeto tiver build.
