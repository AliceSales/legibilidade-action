import sys
import os
import requests
from pathlib import Path

api_url = sys.argv[1].rstrip("/")

try:
    print("Acordando API...")
    requests.get(f"{api_url}/", timeout=60)
except Exception as e:
    print(f"Aviso: health check falhou, tentando análise mesmo assim: {e}")

java_files = list(Path(".").rglob("*.java"))

scores = []
results = []

if not java_files:
    report = "## 🤖 Análise de Legibilidade\n\nNenhum arquivo Java encontrado."
    average = 0
    status = "Sem arquivos"
else:
    for file in java_files:
        content = file.read_text(encoding="utf-8", errors="ignore")

        response = requests.post(
            f"{api_url}/analyze",
            json={
                "filename": str(file),
                "content": content
            },
            timeout=90
        )

        response.raise_for_status()
        result = response.json()

        score = float(result.get("score", 0))
        scores.append(score)

        result["score"] = score
        result["warnings"] = result.get("warnings", [])
        result["label"] = result.get("label", "Sem classificação")
        result["pontuacao_sobrecarga"] = result.get("pontuacao_sobrecarga", None)
        result["nota_modelo_1_a_5"] = result.get("nota_modelo_1_a_5", None)

        results.append(result)

    average = sum(scores) / len(scores)
    status = "Aprovado ✅" if average >= 7 else "Reprovado ❌"

    lines = []
    lines.append("## 🤖 Análise de Legibilidade")
    lines.append("")
    lines.append(f"**Média final:** `{average:.2f}/10`")
    lines.append(f"**Status:** {status}")
    lines.append("")
    lines.append("| Arquivo | Nota /10 | Sobrecarga /20 | Modelo /5 | Classificação | Avisos |")
    lines.append("|---|---:|---:|---:|---|---|")

    for result in results:
        warnings = "<br>".join(result["warnings"]) if result["warnings"] else "Nenhum"

        sobrecarga = (
            f"{float(result['pontuacao_sobrecarga']):.2f}"
            if result["pontuacao_sobrecarga"] is not None
            else "-"
        )

        nota_modelo = (
            f"{float(result['nota_modelo_1_a_5']):.2f}"
            if result["nota_modelo_1_a_5"] is not None
            else "-"
        )

        lines.append(
            f"| `{result['filename']}` | {result['score']:.2f} | {sobrecarga} | {nota_modelo} | {result['label']} | {warnings} |"
        )

    report = "\n".join(lines)

print(report)

with open("legibilidade-report.md", "w", encoding="utf-8") as f:
    f.write(report)

github_output = os.environ.get("GITHUB_OUTPUT")
if github_output:
    with open(github_output, "a", encoding="utf-8") as f:
        f.write(f"average={average:.2f}\n")
        f.write(f"status={status}\n")