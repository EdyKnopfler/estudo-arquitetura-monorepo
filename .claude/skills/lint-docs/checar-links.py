"""Checagens mecânicas da doc: links quebrados, docs fora do índice, código citando doc inexistente."""
import re
import subprocess
import sys
from pathlib import Path

raiz = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
problemas = []

def linha_de(texto, pos):
    return texto.count("\n", 0, pos) + 1

def ancoras(md: Path):
    def slug(titulo):
        s = re.sub(r"[^\w\- ]", "", titulo.strip().lower())
        return s.replace(" ", "-")
    return {slug(m.group(1)) for m in re.finditer(r"^#+\s+(.+)$", md.read_text(), re.M)}

mds = [raiz / "README.md", raiz / "CLAUDE.md", *sorted((raiz / "docs").glob("*.md"))]
for md in mds:
    texto = md.read_text()
    for m in re.finditer(r"\[[^\]]*\]\(([^)\s]+)\)", texto):
        alvo = m.group(1)
        if re.match(r"[a-z]+:", alvo):
            continue
        caminho, _, ancora = alvo.partition("#")
        destino = (md.parent / caminho).resolve() if caminho else md
        onde = f"{md.relative_to(raiz)}:{linha_de(texto, m.start())}"
        if not destino.exists():
            problemas.append(f"{onde}: link quebrado -> {alvo}")
        elif ancora and destino.suffix == ".md" and ancora not in ancoras(destino):
            problemas.append(f"{onde}: âncora inexistente -> {alvo}")

indice = (raiz / "docs" / "README.md").read_text()
# todo/to-decide ficam fora do índice de propósito: só se leem sob pedido (CLAUDE.md, Foco)
fora_do_indice = {"README.md", "todo.md", "to-decide.md"}
for doc in sorted((raiz / "docs").glob("*.md")):
    if doc.name not in fora_do_indice and f"({doc.name})" not in indice:
        problemas.append(f"docs/{doc.name}: fora do índice docs/README.md")

rastreados = subprocess.run(["git", "-C", str(raiz), "ls-files"], capture_output=True, text=True).stdout.split()
for rel in rastreados:
    if rel.endswith(".md"):
        continue
    arq = raiz / rel
    try:
        texto = arq.read_text()
    except (UnicodeDecodeError, OSError):
        continue
    for m in re.finditer(r"docs/([\w\-]+\.md)", texto):
        if not (raiz / "docs" / m.group(1)).exists():
            problemas.append(f"{rel}:{linha_de(texto, m.start())}: cita doc inexistente -> docs/{m.group(1)}")

print("\n".join(problemas) if problemas else "nenhum problema mecânico")
