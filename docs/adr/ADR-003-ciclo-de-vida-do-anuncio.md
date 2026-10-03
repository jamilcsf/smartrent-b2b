# ADR-003 — Ciclo de vida do anúncio de imóvel

**Status:** Aceita
**Data:** 2026-10-03
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

O catálogo, o painel e a gestão de reservas já existiam, mas o cadastro de imóveis era feito por
SQL, não havia papel de usuário comum (todo cadastro virava `ANFITRIAO`), nem mídias, preço em
etapas, publicação, edição de anúncio no ar ou garantia de que reajustes não alterassem reservas
já criadas. Esta ADR registra as decisões do fluxo criar → pré-publicar → publicar → editar.

## Decisões

1. **Papéis.** `PapelUsuario` ganha `CLIENTE` (menor privilégio, padrão do cadastro). `ANFITRIAO` é o
   gestor. O cadastro público nunca cria `ADMIN`. A autorização é do servidor: `SecurityConfig` exige
   papel de gestor em `/api/gestor/**` e `/api/reservas/**`, e `ImovelAcesso` confere a propriedade de
   cada imóvel (403 para imóvel alheio). Esconder botões no front é só conveniência.
2. **Máquina de estados explícita.** `StatusAnuncio` define as transições permitidas;
   `MaquinaDeEstados.mover` é o único caminho para trocar o status. Todo "agora" vem de um `Clock`
   injetado, truncado em microssegundos (`Agora`), o que permite testar prazos sem esperar.
3. **Relógio das 24h.** `preco_primeira_confirmacao_em` é gravado uma única vez; mudar o preço
   (manual ou IA) nunca o reescreve. A publicação revalida o prazo no servidor e **não depende** do
   job: o status `PRONTO_PARA_PUBLICAR` é um reflexo do tempo (job + promoção a cada leitura).
4. **Edição de anúncio no ar.** Iniciar a edição tira o anúncio do ar na hora e cria um rascunho
   (`anuncio_rascunhos`, JSON dos campos + ordem/capa das mídias); o anúncio vivo só muda na
   confirmação. Mídia nova nasce `NOVA` e removida vira `REMOVIDA` (continua no anúncio vivo), de modo
   que o descarte restaura **exatamente** o estado anterior. Não há prazo máximo de edição: nenhum job
   expira, republica ou descarta `EM_EDICAO`. O relógio de 2h começa só em "Confirmar alteração"
   (`republicar_em` fixo). O descarte não aplica suspensão, não exige aceite, é auditado e fica
   bloqueado depois de confirmar. Descartar uma edição iniciada sobre `REPUBLICACAO_AGENDADA`
   restaura o `republicar_em` original (ou vai direto a `PUBLICADO` se o horário já passou).
5. **Republicação robusta.** `PromocaoAnuncioJob` promove em lote com `UPDATE` condicional
   (idempotente) e, além disso, a consulta do catálogo trata `REPUBLICACAO_AGENDADA` com
   `republicar_em <= agora` como visível (`ImovelRepository.VISIVEL`): falha ou atraso do job não
   deixa o anúncio fora do ar além do prazo.
6. **Lembrete de edição esquecida.** `LembreteEdicaoJob` apenas lê `EM_EDICAO` e notifica
   (in-app + `NotificadorEmail`; sem SMTP no projeto, a implementação padrão só registra em log e o
   canal se chama `EMAIL_LOG` para não fingir envio). Idempotência pela chave única
   `(imovel, edicao_iniciada_em, numero, canal)`; falha de envio é registrada na linha e retentada na
   execução seguinte, sem tocar o imóvel. Um job atrasado envia só o lembrete mais recente.
7. **Snapshot de preço nas reservas.** A reserva grava diária por data, número de diárias, taxas,
   total, moeda e título/endereço/características do imóvel; as colunas são `updatable=false`. O
   `valorTotal` do cliente HTTP é ignorado. Mudar as datas de uma reserva recalcula o total com a
   diária do snapshot, nunca com o preço atual. Reservas legadas recebem snapshot reconstruído na
   migration V6. Novas reservas só são aceitas se o imóvel está no catálogo.
8. **Mídia.** `MidiaStorage` (interface) com implementação em disco; trocar por S3 é escrever outra
   implementação. Tipo, dimensões e duração são verificados no servidor pelo conteúdo do arquivo (não
   pelo que o navegador declara). Duração de vídeo é lida do cabeçalho `moov/mvhd` do MP4/MOV sem
   dependência nova; só MP4/MOV são aceitos. O limite de 14 conta fotos comuns e 360 somadas, com o
   imóvel travado (`PESSIMISTIC_WRITE`) para uploads simultâneos. URLs usam UUID aleatório.
9. **IA de precificação.** `PricingSuggestionService` (interface) com provedor Groq via `RestClient`
   síncrono, prazo e modelo por ambiente. Só vão atributos do imóvel e a data; título, descrição,
   endereço exato e dados do gestor não saem. A IA só sugere; falha vira mensagem clara e edição manual.
10. **Testes de consulta.** H2 em escopo de teste valida as consultas JPQL (visibilidade, promoções,
    chave única) sem exigir PostgreSQL no CI; as migrations foram validadas contra PostgreSQL 16.

## Suposições a validar

- Gestores se cadastram sozinhos pelo seletor da tela de cadastro (não há aprovação).
- O termo de uso (`TermoUso`, versão 1.0) é um texto de trabalho e precisa de revisão jurídica.
- Reservas são registradas pelo gestor; não há fluxo de reserva autoatendido por cliente.
- Preço de anúncio publicado só muda pelo fluxo de edição.
- O "portal da transparência" é o `index.html`; a sugestão de IA em lote foi acrescentada a ele.

## Consequências

- Aumenta a superfície de testes (relógio controlado, H2) e exige PostgreSQL 16 para validar
  migrations.
- Mídia em disco local não escala horizontalmente: em produção com mais de uma instância, trocar a
  implementação de `MidiaStorage`.
- Notificações por e-mail dependem de uma implementação real de `NotificadorEmail`.
