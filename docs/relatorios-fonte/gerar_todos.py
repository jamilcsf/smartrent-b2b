#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gera (ou regenera) todos os documentos PDF do SmartRent B2B.

Uso:
    python3 gerar_todos.py              # gera todos os diagramas + todos os PDFs
    python3 gerar_todos.py --so-pdfs    # pula a regeração dos diagramas (mais rápido,
                                          # use quando só o texto mudou, não os diagramas)

Os arquivos finais saem em ./out/.
"""
import subprocess
import sys
import os

BASE = os.path.dirname(os.path.abspath(__file__))
os.chdir(BASE)

SO_PDFS = "--so-pdfs" in sys.argv

DIAGRAMAS = ["make_diagrams.py", "make_bmc_diagram.py"]
DOCUMENTOS = [
    "build_01_relatorio.py",
    "build_02_cronograma.py",
    "build_03_riscos.py",
    "build_04_testes.py",
    "build_05_rastreabilidade.py",
    "build_06_bmc.py",
]

os.makedirs("out", exist_ok=True)

scripts = DOCUMENTOS if SO_PDFS else DIAGRAMAS + DOCUMENTOS

for script in scripts:
    print(f"--- {script} ---")
    result = subprocess.run([sys.executable, script], cwd=BASE)
    if result.returncode != 0:
        print(f"FALHOU: {script} (código {result.returncode})")
        sys.exit(result.returncode)

print("\nTodos os documentos foram gerados em out/.")
