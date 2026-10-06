# ADR-008 — Auditoria de segurança do SmartChat e dos uploads (correções)

**Status:** Aceita
**Data:** 2026-10-06
**Contexto do projeto:** SmartRent B2B — Projeto Aplicado IV, UniSENAI/ADS

---

## Contexto

Depois da cifragem em repouso ([ADR-006](ADR-006-cifragem-em-repouso-do-smartchat.md)) e do reforço do chat e dos uploads
([ADR-007](ADR-007-reforco-de-seguranca-do-smartchat-e-dos-uploads.md)), foi feita uma auditoria em quatro fases (mapeamento,
auditoria, correção e verificação) sobre a base `jamil/main`. A auditoria classificou 21 achados (1 crítico, 5 altos, 9 médios e
6 baixos) e confirmou, por teste, que o filtro de contatos podia ser contornado. Esta ADR registra as decisões tomadas nas
correções. Nada aqui foi marcado como V2 sem decisão explícita; o que ficou de fora está na seção "Visão Futura (V2)".

## Decisões

### 1. Máscara de contatos externos (`MessageFilterService`)

O filtro já existia e roda no servidor **antes** de persistir. Os contornos confirmados eram todos de normalização, então a
correção está na entrada do filtro, não em regex novas por contato:

- `normalizarUnicode`: remove caracteres invisíveis (zero-width, soft hyphen, seletores de variação, o quadrinho dos dígitos em emoji,
  controles bidi), aplica NFKC (dígitos e pontos fullwidth, espaços especiais, dígitos matemáticos) e converte qualquer dígito não ASCII
  (árabe-índico etc.) e o ponto ideográfico. O texto devolvido ao usuário sai da versão normalizada; `texto_original` guarda o que foi digitado.
- E-mail e domínio escritos com letras espaçadas (`j o a o @ g m a i l . c o m`, `g m a i l ponto com`) são procurados no texto
  com os espaços entre tokens de um caractere removidos, com índice de volta ao texto original.
- Telefone misturando número por extenso e dígitos: sequência de dígitos e palavras-número com 10 ou mais algarismos e ao menos uma palavra.
  Datas, preços, CEP, código de reserva e quantidades chegam mascarados (letra neutra) e não formam a sequência.
- Mensageiros e redes citados **sozinhos** (`whatsapp`, `wpp`, `zap`, `telegram`, `insta`, `facebook`, `discord`, `skype`...) passam a ser
  mascarados na categoria nova `CONTATO_EXTERNO`.
- **"pix" continua só como alerta** (`SUSPEITA_FRAUDE`, sem mascarar): a palavra isolada tem uso legítimo. Foram acrescentadas frases
  de alerta ("meu pix", "pix por fora", "pix para mim"...). Decisão do responsável pelo projeto.
- Substituição: o marcador do borrão do chat (já existente, não forjável, com preenchimento aleatório) no lugar do literal
  `[CONTATO BLOQUEADO]`; o indicador `masked` da auditoria é `Resultado.alterado()` e `ocorrencias` na mensagem.

**Consequência:** a detecção continua heurística. Quem combina o contato por outro meio ("me procura pelo nome do meu perfil") não é
pego; detecção semântica é V2.

### 2. Remoção de EXIF/GPS e validação de upload

A auditoria confirmou que a foto de anúncio e a de perfil já eram recodificadas com `ImageIO`, sem metadados, com tipo pelos
primeiros bytes (JPEG/PNG), limite de tamanho e nome UUID. Não foi adicionada dependência de imagem. Mudanças:

- **Teto de pixels de 60 MP para 30 MP** (`MIDIA_MAX_PIXELS_IMAGEM`): a imagem é decodificada por inteiro (4 bytes por pixel mais a
  cópia da regravação), o que derrubava uma instância de 512 MB. 30 MP cobrem uma foto 360 de 7680x3840.
- **Vídeo sem FFmpeg é recusado** fora de `dev`/`test` (`VideoProcessingIndisponivel`): o modo básico não remove metadados nem GPS.
- `/api/midias/{chave}/miniatura` usa a mesma regra de visibilidade das demais rotas.
- Risco aceito: mídia em estado `NOVA` (rascunho de edição) continua acessível pela chave UUIDv4 porque `<img>` não envia token.
- Não existe upload de comprovantes no código; comprovantes e imagens no chat são V2.

### 3. UUID público versus chave primária

Mantém-se a chave primária numérica interna (migrar a PK é arriscado para o prazo). O que sai pela API:

- Conversa: `codigo_publico` (V19, já existente).
- **Mensagem: `codigo_publico` (V20, único, não nulo, backfill com `gen_random_uuid()`).** O cursor `depoisDe` e as denúncias usam o
  código; um código de outra conversa ou desconhecido volta ao início e não revela nada. `ReservaResumo` perdeu o id.
- Reserva, notificação e imóvel **continuam com id numérico nas rotas** (decisão de prazo). Em vez de UUID, a API responde **404 idêntico**
  ao de id inexistente quando o registro é de outra pessoa (`AcessoOcultoException`, subclasse de `AcessoNegadoException`).
  Falta de papel continua 403 porque não depende de existir registro. Um anúncio não publicado responde como imóvel inexistente ao abrir o chat.
- Verificação de propriedade em cada endpoint do chat: confirmada pela auditoria (não havia IDOR) e coberta por teste.

### 4. Sanitização e validação de entrada

- `Sanitizador` passou a usar o **OWASP Java HTML Sanitizer** com a política "nenhum HTML": remove elementos, atributos de evento e o
  **conteúdo** de `script`/`style`, inclusive em HTML malformado e em entidades, e devolve texto puro. Um `<` solto ("preço < 100") é preservado.
- O front já usava escape em tudo (`UI.escapar`, `textContent`); as ~45 interpolações em `innerHTML` do chat passam por ele.
- Todas as consultas usam parâmetros nomeados (a auditoria não encontrou concatenação).
- Bean Validation (`@NotBlank`, `@Size`) e `@Valid` nos pedidos do chat; teto de 1 MB para corpo JSON (`LimiteDeCorpoFilter`).
- Erros: `IllegalArgumentException` de biblioteca devolve mensagem genérica; `server.error.*` fixado em `never`.
- Cabeçalhos: CSP com `object-src`, `base-uri`, `frame-ancestors` e `form-action`, Referrer-Policy e Permissions-Policy. A CSP completa
  (com `script-src` por nonce) exige tirar os scripts inline e o Tailwind por CDN: V2.

### 5. Credenciais e acesso

- O **JAR versionado** em `entrega-isi/origem/` embutia URL, usuário e senha do banco como literais: removido e ignorado (`*.jar`).
  **O histórico não foi reescrito: a senha precisa ser rotacionada** (ver "Roteiro" abaixo).
- `JWT_SECRET` sem valor padrão: fora de `dev`/`test` a aplicação não sobe sem ele (≥ 32 bytes) nem com o segredo público do repositório.
- reCAPTCHA: fora de `dev`/`test` a aplicação não sobe com a chave de teste do Google (aprovaria qualquer token).
  **`RECAPTCHA_SITE_KEY` e `RECAPTCHA_SECRET` reais passam a ser obrigatórios no Render.**
- Login: 5 falhas por e-mail ou 20 por IP em 15 minutos bloqueiam (429); cadastro limitado a 10 por IP por hora. Troca aceita: um terceiro pode
  atrasar o login de uma vítima por alguns minutos; o captcha continua exigido em toda tentativa.
- `GET /api/precificacao/sugerir` (público, gravava e chamava a IA) passou a exigir gestor dono do imóvel.
- CORS: removidos os `@CrossOrigin("*")`; origens externas só por `SMARTRENT_CORS_ORIGENS`.
- SSE: no máximo 5 conexões e 5 tickets pendentes por usuário.
- `.gitignore` (`.env.*`, chaves, `application-local/prod*`), `.env.example`, CI com permissões mínimas, actions fixadas por SHA, varredura
  de segredos e Dependabot; imagens Docker com versão fixa.
- Spring Boot 3.2.3 (sem suporte) → **3.5.16**, com Flyway 11 e `flyway-database-postgresql`.

### 6. Cifra: versão 2 com contexto autenticado

`CifraCampo` grava `v2:` e autentica o contexto `tabela.coluna` (AAD) por um conversor JPA por coluna. Um valor copiado entre colunas ou
tabelas falha na tag. `v1` (sem AAD) segue legível. Um valor ilegível aparece como "[mensagem indisponível]" e vai ao log sem conteúdo,
em vez de derrubar a leitura da conversa.

**Limites assumidos:** o id da linha não entra no AAD (o conversor JPA não o conhece), então trocar valores entre linhas da mesma
coluna continua possível a quem tem escrita no banco; a leitura de texto sem prefixo (legado) segue aceita; se **todos** os valores falharem,
a causa é a chave trocada (`SMARTCHAT_CRYPTO_KEY`).

## Roteiro: limpar a credencial do histórico (NÃO executado)

1. **Rotacionar a senha do banco no Supabase agora** (a antiga continua nos clones e no histórico).
2. Com o repositório espelho (`git clone --mirror`), rodar `git filter-repo --path entrega-isi/origem/SmartRentB2B.jar --invert-paths`.
3. Forçar o push para o remoto `jamil` (autorização explícita do responsável), pedir que todos refaçam o clone e avisar que forks e PRs antigos
   ainda contêm o JAR.
4. Pedir ao GitHub a limpeza de caches e visões em cache, se o repositório for público.

## Consequências

- Mensagens têm `codigo_publico` novo: a V20 precisa rodar no Supabase (backfill automático). Os dados cifrados antigos (`v1`) continuam legíveis.
- O Render precisa de `RECAPTCHA_SITE_KEY` e `RECAPTCHA_SECRET` reais e de `JWT_SECRET`; sem eles o serviço não sobe (de propósito).
- O front do chat envia e recebe `codigo` (UUID) no lugar de `id`.
- Os testes automatizados cobrem cada correção (faixa `CT1000–CT1102`); a suíte passa com 671 testes.

## Visão Futura (V2) — não implementado

- Negar por padrão no `SecurityConfig` (hoje `anyRequest().permitAll()`); o `InventarioDeRotasTest` obriga a classificar cada controller novo.
- UUID público também em reserva, notificação, imóvel e usuário (hoje 404 uniforme com id numérico).
- CSP completa com nonce e Tailwind compilado, sem scripts inline.
- Moderação por IA e detecção semântica de tentativas de burlar o filtro (combinar contato sem escrevê-lo).
- Imagens e comprovantes no chat, com o mesmo saneamento de metadados e moderação de conteúdo.
- AAD com o id da linha; rotação de chave da cifra (`v3`) e rotina de reencriptação; fim da leitura de texto puro legado depois da migração.
- Rate limit distribuído (Redis); hoje é em memória, por instância, e zera no reinício.
- Notificação por e-mail do chat e SMTP/Brevo de ponta a ponta; retenção de `texto_original`, `texto_hmac` e dos alertas internos (jurídico).
- Scan de dependências com banco de CVEs no CI (o Dependabot cobre as atualizações; o scan completo exige chave NVD).
