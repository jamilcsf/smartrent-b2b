# Documentação do SmartRent B2B — código-fonte dos PDFs

Esta pasta gera os 6 documentos PDF do projeto (Relatório Técnico, Cronograma,
Matriz de Riscos, Plano de Testes, Matriz de Rastreabilidade, Business Model
Canvas). Eles **não são editados diretamente** — são gerados por estes
scripts Python (reportlab + matplotlib), para manter formatação ABNT
consistente entre eles.

## Instalação

```bash
pip install -r requirements.txt --break-system-packages
```

## Gerar tudo

```bash
python3 gerar_todos.py
```

Os PDFs saem em `out/`. Para regenerar só o texto (sem recriar os diagramas,
mais rápido):

```bash
python3 gerar_todos.py --so-pdfs
```

## Estrutura

| Arquivo | Gera |
|---|---|
| `pdf_common.py` | Estilos ABNT compartilhados (margens, fontes, capa, quadro/figura, numeração de página, sumário). Editar aqui afeta **todos** os documentos. |
| `make_diagrams.py` | Os 5 diagramas técnicos (classes, casos de uso, 2 sequências, Gantt) → `assets/*.png` |
| `make_bmc_diagram.py` | O diagrama do Business Model Canvas → `assets/business_model_canvas.png` |
| `build_01_relatorio.py` | Documento 1 — Relatório Técnico |
| `build_02_cronograma.py` | Documento 2 — Cronograma |
| `build_03_riscos.py` | Documento 3 — Matriz de Riscos |
| `build_04_testes.py` | Documento 4 — Plano de Testes |
| `build_05_rastreabilidade.py` | Documento 5 — Matriz de Rastreabilidade |
| `build_06_bmc.py` | Documento 6 — Business Model Canvas |

## Quando atualizar o quê

Esta tabela existe para o Claude Code (ou qualquer integrante da equipe)
saber, ao concluir um componente, quais documentos revisar — o mesmo
espírito do protocolo de `STATUS_PROJETO.md` e das ADRs, aplicado aos PDFs.

| Mudança no projeto | Editar | Seção |
|---|---|---|
| Novo requisito funcional/não funcional, ou mudança de escopo MoSCoW | `build_01_relatorio.py` | RF/RNF (Seção 3) — e replicar o ID novo em `build_05_rastreabilidade.py` |
| Nova entidade, campo ou relacionamento no domínio | `make_diagrams.py` (diagrama de classes) **e** `build_01_relatorio.py` (Seção 6, Modelo de Dados) | — |
| Nova migration / alteração de schema real | `build_01_relatorio.py`, Seção 6.2 — conferir se o Quadro de cada tabela ainda bate com o `V1__...sql` real | 6.2 |
| Novo caso de uso | `build_01_relatorio.py` (Seção 4) **e** `make_diagrams.py` (diagrama de casos de uso) | 4 |
| Mockups de tela anexados | `build_01_relatorio.py`, Seção 7 — trocar o Quadro placeholder por `figura(...)` com o PNG real | 7 |
| Prazo, fase ou entrega do cronograma mudou | `build_02_cronograma.py` | — |
| Novo risco identificado, ou risco mitigado/encerrado | `build_03_riscos.py` | — |
| Caso de teste implementado (mudar status) ou caso novo | `build_04_testes.py` **e** `build_05_rastreabilidade.py` (coluna de casos de teste) | — |
| Mudança no modelo de receita, custos, canais etc. | `build_06_bmc.py` **e** `make_bmc_diagram.py` (o canvas visual) — mantenha os dois em sincronia | — |
| Nova decisão arquitetural relevante para o negócio | Considerar se vale registrar em `build_01_relatorio.py` (Introdução/Solução) além da ADR técnica em `docs/` | 1.2 |

## Cuidado ao editar `make_bmc_diagram.py` / `make_diagrams.py`

Os textos dentro das caixas usam quebra de linha por **medição real** da
largura do texto renderizado (`ax.transData.transform` +
`get_window_extent`), não por contagem de caracteres — é o que evita texto
vazando da caixa. Ao adicionar itens novos a uma caixa, rode o script e
confira visualmente (`out/*.png` ou abra o PDF); se aparecer o aviso
`AVISO: bloco '...' excede a caixa`, aumente a altura da célula (parâmetro
`h` do `cell(...)`) ou reduza `item_fs`.

## Nomes fixos no documento

Autores e orientadora estão hardcoded em `pdf_common.py`
(`AUTORES`, `ORIENTADORA`). Mudar ali propaga para os 6 documentos.
