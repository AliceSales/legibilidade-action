import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

public class AnalyzerAction {

    static class Payload {
        String filename;
        Map<String, Integer> features;

        Payload(String filename, Map<String, Integer> features) {
            this.filename = filename;
            this.features = features;
        }
    }

    public static void main(String[] args) throws Exception {
        String apiUrl = args[0].replaceAll("/$", "");
        Gson gson = new Gson();
        HttpClient client = HttpClient.newHttpClient();

        List<Path> javaFiles = Files.walk(Paths.get("."))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/.git/"))
                .toList();

        if (javaFiles.isEmpty()) {
            System.out.println("Nenhum arquivo Java encontrado.");
            return;
        }

        List<Double> scores = new ArrayList<>();
        StringBuilder report = new StringBuilder();

        report.append("## 🤖 Análise de Legibilidade\n\n");
        report.append("| Arquivo | Nota /10 | Sobrecarga /20 | Modelo /5 | Classificação |\n");
        report.append("|---|---:|---:|---:|---|\n");

        for (Path file : javaFiles) {
            Map<String, Integer> features = extractFeatures(file);

            Payload payload = new Payload(file.toString(), features);
            String json = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/analyze"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Erro na API: " + response.body());
            }

            Map result = gson.fromJson(response.body(), Map.class);

            double score = ((Number) result.get("score")).doubleValue();
            double sobrecarga = ((Number) result.get("pontuacao_sobrecarga")).doubleValue();
            double notaModelo = ((Number) result.get("nota_modelo_1_a_5")).doubleValue();
            String label = String.valueOf(result.get("label"));

            scores.add(score);

            report.append("| `")
                    .append(file)
                    .append("` | ")
                    .append(String.format("%.2f", score))
                    .append(" | ")
                    .append(String.format("%.2f", sobrecarga))
                    .append(" | ")
                    .append(String.format("%.2f", notaModelo))
                    .append(" | ")
                    .append(label)
                    .append(" |\n");
        }

        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        String status = average >= 7 ? "Aprovado ✅" : "Reprovado ❌";

        report.insert(0,
                "**Média final:** `" + String.format("%.2f", average) + "/10`\n\n" +
                "**Status:** " + status + "\n\n"
        );

        Files.writeString(Paths.get("legibilidade-report.md"), report.toString());

        System.out.println(report);

        if (average < 7) {
            System.exit(1);
        }
    }

    private static Map<String, Integer> extractFeatures(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);

        int pParametros = cu.findAll(MethodDeclaration.class)
                .stream()
                .mapToInt(method -> method.getParameters().size())
                .sum();

        int cComplexos =
                cu.findAll(com.github.javaparser.ast.stmt.IfStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.ForStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.DoStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).size()
                        + cu.findAll(com.github.javaparser.ast.stmt.CatchClause.class).size();

        int bBooleanos =
                cu.findAll(BinaryExpr.class).stream()
                        .filter(expr ->
                                expr.getOperator() == BinaryExpr.Operator.AND ||
                                expr.getOperator() == BinaryExpr.Operator.OR
                        )
                        .toList()
                        .size()
                +
                cu.findAll(UnaryExpr.class).stream()
                        .filter(expr -> expr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT)
                        .toList()
                        .size();

        int fdDesordem =
                cu.getAllContainedComments().stream()
                        .mapToInt(comment -> comment.getContent().contains("TODO") ? 1 : 0)
                        .sum()
                +
                cu.findAll(MethodCallExpr.class).stream()
                        .mapToInt(call -> call.toString().contains("System.out.println") ? 1 : 0)
                        .sum()
                +
                (int) Files.readAllLines(file).stream()
                        .filter(line -> line.length() > 100)
                        .count();

        int pnNomesCurtos =
                cu.findAll(VariableDeclarator.class).stream()
                        .mapToInt(variable -> variable.getNameAsString().length() <= 2 ? 1 : 0)
                        .sum()
                +
                cu.findAll(MethodDeclaration.class).stream()
                        .mapToInt(method ->
                                (int) method.getParameters().stream()
                                        .filter(param -> param.getNameAsString().length() <= 2)
                                        .count()
                        )
                        .sum();

        Map<String, Integer> features = new LinkedHashMap<>();
        features.put("P_parametros", pParametros);
        features.put("C_complexos", cComplexos);
        features.put("B_booleanos", bBooleanos);
        features.put("FD_desordem", fdDesordem);
        features.put("PN_nomes_curtos", pnNomesCurtos);

        return features;
    }
}