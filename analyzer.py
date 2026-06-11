import sys
import requests
from pathlib import Path

api_url = sys.argv[1].rstrip("/")

java_files = list(Path(".").rglob("*.java"))

if not java_files:
    print("Nenhum arquivo Java encontrado.")
    sys.exit(0)

print(f"Arquivos Java encontrados: {len(java_files)}")

scores = []

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

    print("----------------------------------")
    print(f"Arquivo: {result['filename']}")
    print(f"Nota: {result['score']}")
    print(f"Classificação: {result['label']}")

    if result["warnings"]:
        print("Avisos:")
        for warning in result["warnings"]:
            print(f"- {warning}")

average = sum(scores) / len(scores)

print("==================================")
print(f"Média final do projeto: {average:.2f}")

if average < 7:
    print("Projeto reprovado na análise de legibilidade.")
    sys.exit(1)

print("Projeto aprovado na análise de legibilidade.")