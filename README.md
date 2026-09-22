# Projeto_Aplicado_IV

# 🏖️ Sistema B2B de Gestão Inteligente para Aluguel por Temporada

Plataforma web B2B para anfitriões independentes, pousadas de pequeno porte e gestores de imóveis (*property managers*) da Grande Florianópolis, com sugestão de preços diários via Inteligência Artificial baseada em sazonalidade e localização.

> Projeto Aplicado IV — Curso de Análise e Desenvolvimento de Sistemas (ADS), UniSENAI.

---

## 📌 Sobre o Projeto

Anfitriões e gestores de imóveis frequentemente definem o preço da diária de forma subjetiva, sem considerar de maneira estruturada a sazonalidade, o dia da semana e a localização do imóvel. Este sistema centraliza o cadastro de imóveis e reservas e utiliza uma API de IA (Groq/OpenAI) para sugerir preços competitivos, reduzindo a subjetividade da precificação e o tempo gasto em tarefas operacionais.

## ✨ Funcionalidades

- 🔐 Autenticação de usuários (anfitrião/gestor), reaproveitada e evoluída do Projeto Aplicado III
- 🏠 Cadastro e gestão completa de imóveis (CRUD)
- 📅 Cadastro e gestão de reservas, com validação de conflito de datas
- 🤖 Sugestão de preço diário via IA, contextualizada por bairro, data e sazonalidade
- 📊 Visualização gráfica da evolução dos preços sugeridos (Chart.js)
- 🗂️ Histórico de sugestões de preço geradas

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Backend | Java 17+, Spring Boot 3.x (Spring Web, Spring Data JPA) |
| Integração com IA | `RestClient` nativo, consumindo API Groq/OpenAI |
| Banco de Dados | PostgreSQL (hospedado no Supabase) |
| Frontend | HTML5, CSS3 responsivo (mobile-first), JavaScript Vanilla, Chart.js |
| Testes | JUnit 5, Mockito |
| CI/CD | GitHub Actions |
| Deploy | Render (backend) + Vercel (frontend) + Supabase (banco) |

## 📁 Estrutura do Projeto

```
src/main/java/com/temporada/gestao
├── controller     # Camada de entrada das requisições (REST)
├── service        # Regras de negócio, incluindo PrecificacaoIaService
├── repository     # Acesso a dados via Spring Data JPA
├── model          # Entidades: Usuario, Imovel, Reserva, SugestaoPreco
├── dto            # Objetos de transferência de dados (records)
└── config         # Configurações, incluindo RestClientConfig
```

## ✅ Pré-requisitos

- Java 17 ou superior
- Maven 3.8+
- Conta no [Supabase](https://supabase.com) (ou PostgreSQL local para desenvolvimento)
- Chave de API da [Groq](https://console.groq.com) ou OpenAI

## ⚙️ Configuração e Instalação

1. Clone o repositório:
   ```bash
   git clone https://github.com/<sua-org>/gestao-aluguel-temporada.git
   cd gestao-aluguel-temporada
   ```

2. Configure as variáveis de ambiente (nunca versionar credenciais no repositório):
   ```bash
   export DATABASE_URL=jdbc:postgresql://<host-supabase>:5432/postgres
   export DATABASE_USERNAME=<usuario>
   export DATABASE_PASSWORD=<senha>
   export IA_API_KEY=<sua-chave-groq-ou-openai>
   export IA_API_URL=https://api.groq.com/openai/v1/chat/completions
   export IA_API_MODEL=llama-3.3-70b-versatile
   ```

3. Compile e execute os testes:
   ```bash
   mvn clean test
   ```

4. Inicie o backend localmente:
   ```bash
   mvn spring-boot:run
   ```
   A API ficará disponível em `http://localhost:8080`.

5. Abra o frontend (`/frontend`) em um navegador ou sirva com uma extensão como Live Server, apontando as chamadas `fetch()` para a URL do backend.

## 🔌 Principais Endpoints da API

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/auth/registrar` | Cadastra um novo usuário |
| `POST` | `/api/auth/login` | Autentica um usuário |
| `GET` \| `POST` | `/api/imoveis` | Lista ou cadastra imóveis |
| `PUT` \| `DELETE` | `/api/imoveis/{id}` | Atualiza ou remove um imóvel |
| `GET` \| `POST` | `/api/imoveis/{id}/reservas` | Lista ou cadastra reservas de um imóvel |
| `GET` | `/api/imoveis/{id}/sugestao-preco?data=YYYY-MM-DD` | Retorna a sugestão de preço da IA para a data informada |

## 🧪 Testes

O projeto utiliza JUnit 5 e Mockito, isolando a chamada externa à API de IA para evitar dependência de rede e custos desnecessários durante a suíte de testes:

```bash
./mvnw test
```

## 🚀 CI/CD e Deploy

A cada `push` na branch `main`, o GitHub Actions executa build e testes automaticamente (`.github/workflows/ci.yml`). O deploy contínuo ocorre em:

- **Backend:** Render, com variáveis de ambiente configuradas no painel do serviço
- **Frontend:** Vercel, com deploy automático a partir do repositório
- **Banco de Dados:** Supabase (PostgreSQL gerenciado)

## 🗺️ Roadmap (V2)

- Aplicativo nativo em Flutter
- Integração via iCal com OTAs (Airbnb/Booking)
- Módulo de cobrança/assinatura SaaS
- Histórico analítico comparando sugestões da IA com preços efetivamente praticados

## 👥 Equipe

Projeto desenvolvido por Arthur Moreira, Douglas do Carmo e Jamil Cherem como Projeto
Aplicado IV do curso de Análise e Desenvolvimento de Sistemas — UniSENAI.
Orientação: Profa. Milena Maredmi Correa.

## 📄 Licença

Projeto acadêmico desenvolvido para fins avaliativos da disciplina Projeto Aplicado IV.

## ▶️ Como executar

### Windows: um clique

```
executar.bat
```

O script confere os pré-requisitos, sobe o PostgreSQL, compila, inicia a
aplicação, insere os dados de demonstração e abre o navegador. Para encerrar
tudo e descartar o banco, use `parar.bat`.

Pré-requisitos: **JDK 17 ou superior** e **Docker Desktop** em execução. Maven
não é necessário — o projeto traz o Maven Wrapper.

Login de demonstração: `ana@smartrent.dev` / `senhaSegura123`.

### Passo a passo manual


### Pré-requisitos

- **JDK 17** ou superior, com `JAVA_HOME` apontando para ele
- **PostgreSQL** acessível (um container descartável resolve)
- Maven não é necessário se você usar o wrapper do seu ambiente

### 1. Suba um PostgreSQL

```bash
docker run --rm -d --name smartrent-pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:16
```

### 2. Informe as credenciais por variável de ambiente

Nenhuma credencial fica no código-fonte (RNF03).

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/postgres"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="postgres"
export JWT_SECRET="troque-por-um-segredo-de-no-minimo-32-bytes"
```

No Windows (PowerShell), use `$env:DATABASE_URL="..."` e assim por diante.

### 3. Rode a aplicação

```bash
./mvnw spring-boot:run
```

No primeiro boot o Flyway aplica as migrations de `src/main/resources/db/migration`
e o Hibernate valida o mapeamento contra o esquema resultante.

### 4. Acesse

| Página | Endereço |
|---|---|
| Painel e precificação | http://localhost:8080/index.html |
| Catálogo de imóveis | http://localhost:8080/imoveis.html |
| Gestão de reservas | http://localhost:8080/reservas.html |
| Entrar / criar conta | http://localhost:8080/login.html |

O site é público por padrão: dá para navegar sem login. A autenticação identifica
quem está usando, sem bloquear visitantes.

### Testes

```bash
./mvnw test
```

A suíte não depende de banco nem de rede.

## 📁 Estrutura do repositório

```
├── src/                         Aplicação Spring Boot (código, telas e migrations)
├── docs/
│   ├── STATUS_PROJETO.md        Acompanhamento das entregas
│   ├── documentacao-tecnica.md  Documentação técnica consolidada
│   ├── adr/                     Registros de decisão de arquitetura
│   ├── entregas/                Documentos entregues na disciplina
│   ├── design/                  PRD, guia de design e telas dos protótipos
│   └── relatorios-fonte/        Código que gera os 6 PDFs acadêmicos
├── entrega-isi/                 Script de implantação (AV03)
└── .github/workflows/           Integração contínua
```

Os PDFs em `docs/relatorios-fonte/out/` **não são editados à mão**: são gerados
pelos scripts Python daquela pasta. Veja o `README.md` dela antes de alterá-los.
