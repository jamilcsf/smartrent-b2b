# CLAUDE.md — Diretrizes de Operação Local do Repositório

## Comandos de Execução e Build
- Compilar projeto: `mvn -B clean compile`
- Executar testes unitários: `mvn -B test`
- Executar backend Spring Boot: `mvn spring-boot:run`
- Executar frontend localmente: `npx.cmd --yes http-server . -p 5500` (Windows) ou `npx --yes http-server . -p 5500` (Linux/macOS)

## Padrões de Código e Arquitetura
- Backend Java/Spring:
  - Organização: `com.temporada.gestao` subdividido em `controller`, `service`, `repository`, `model`, `dto` e `config`.
  - Camada de DTOs: Utilizar Java Records imutáveis para requests e responses da API REST.
  - Injeção de dependência: Via construtor (evitar `@Autowired` em atributos de classe).
  - Chamadas HTTP para a Groq/OpenAI: Uso exclusivo do `RestClient` síncrono nativo do Spring 3.x com estratégia de fallback implementada no `try-catch`.
- Validações de Negócio:
  - Reservas não podem permitir data final menor ou igual à data inicial.
  - Conflito de reservas deve ser verificado via query nativa ou derivada: `dataInicio < :fim AND dataFim > :inicio`.
- Frontend:
  - Manter dependência zero de frameworks pesados no client (JavaScript Vanilla puro).
  - Componentes e gráficos instanciados via Chart.js sobre tags `<canvas>`.