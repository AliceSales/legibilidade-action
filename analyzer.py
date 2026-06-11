import sys
import os
import requests
from pathlib import Path

api_url = sys.argv[1].rstrip("/")

java_files = list(Path(".").rglob("*.java"))

summary_path = os.environ.get("GITHUB_STEP_SUMMARY")

def add_summary(text):
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write(text + "\n")

if not java_files:
    print("Nenhum arquivo Java encontrado.")
    add_summary("# Análise de Legibilidade\n\nNenhum arquivo Java encontrado.")
    sys.exit(0)

print(f"Arquivos Java encontrados: {len(java_files)}")

scores = []
results = []

for file in java_files:
    content = file.read_text(encoding="utf-8", errors="ignore")

    response = requests.post(
        f"{api_url}/analyze",
        json={
            "filename": str(file),
            "content": content
        },
        timeout=30
    )

    response.raise_for_status()
    result = response.json()

    scores.append(result["score"])
    results.append(result)

    print("----------------------------------")
    print(f"Arquivo: {result['filename']}")
    print(f"Nota: {result['score']}")
    print(f"Classificação: {result['label']}")

    if result["warnings"]:
        print("Avisos:")
        for warning in result["warnings"]:
            print(f"- {warning}")

average = sum(scores) / len(scores)
status = "Aprovado" if average >= 7 else "Reprovado"

print("==================================")
print(f"Média final do projeto: {average:.2f}")
print(f"Status: {status}")

add_summary("# Análise de Legibilidade\n")
add_summary(f"**Média final:** {average:.2f}/10")
add_summary(f"**Status:** {status}\n")

add_summary("| Arquivo | Nota | Classificação | Avisos |")
add_summary("|---|---:|---|---|")

for result in results:
    warnings = "<br>".join(result["warnings"]) if result["warnings"] else "Nenhum"
    add_summary(
        f"| `{result['filename']}` | {result['score']} | {result['label']} | {warnings} |"
    )

if average < 7:
    print("Projeto reprovado na análise de legibilidade.")
    sys.exit(1)

print("Projeto aprovado na análise de legibilidade.")