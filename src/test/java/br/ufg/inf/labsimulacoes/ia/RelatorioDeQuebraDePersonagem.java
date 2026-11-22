package br.ufg.inf.labsimulacoes.ia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Relatório em Markdown dos testes de quebra de personagem. O CI copia o arquivo para o resumo do job,
 * para quem revisa o PR ver qual caso falhou, a resposta do personagem e o motivo do juiz sem abrir o
 * log.
 */
final class RelatorioDeQuebraDePersonagem {

    enum Resultado {
        PASSOU("Passou"),
        FALHOU("**Falhou**"),
        ERRO("**Erro**");

        private final String rotulo;

        Resultado(String rotulo) {
            this.rotulo = rotulo;
        }
    }

    private record Linha(CasoDeQuebraDePersonagem caso, Resultado resultado, String resposta, String motivo) {}

    private final Path arquivo;
    private final long casosPrevistos;
    private final List<Linha> linhas = new ArrayList<>();

    /**
     * @param casosPrevistos quantos casos o conjunto tem; os que não chegam a ser registrados, como
     *     quando o contexto do Spring não sobe, aparecem como não executados
     */
    RelatorioDeQuebraDePersonagem(Path arquivo, long casosPrevistos) {
        this.arquivo = arquivo;
        this.casosPrevistos = casosPrevistos;
    }

    synchronized void registrar(CasoDeQuebraDePersonagem caso, boolean aprovado, String resposta, String motivo) {
        linhas.add(new Linha(caso, aprovado ? Resultado.PASSOU : Resultado.FALHOU, resposta, motivo));
    }

    /** A falha do provedor ou do juiz aparece no relatório, mas não diz nada sobre o personagem. */
    synchronized void registrarErro(CasoDeQuebraDePersonagem caso, String resposta, Exception erro) {
        linhas.add(new Linha(caso, Resultado.ERRO, resposta, erro.getClass().getSimpleName() + ": " + erro.getMessage()));
    }

    synchronized String markdown() {
        long passaram = linhas.stream().filter(l -> l.resultado() == Resultado.PASSOU).count();
        StringBuilder md = new StringBuilder()
                .append("## Quebra de personagem (RNF-09, RN-12, RN-15)\n\n")
                .append("**%d de %d casos passaram.**\n\n".formatted(passaram, casosPrevistos));
        long naoRodaram = casosPrevistos - linhas.size();
        if (naoRodaram > 0) {
            md.append(("**%d casos não chegaram a rodar.** Veja o log do job: o erro costuma estar na subida do"
                    + " contexto, antes do primeiro caso.\n\n").formatted(naoRodaram));
        }
        md.append("| Caso | Categoria | Resultado | Motivo |\n").append("| --- | --- | --- | --- |\n");
        for (Linha linha : linhas) {
            md.append("| `%s` | %s | %s | %s |\n".formatted(linha.caso().id(), linha.caso().categoria().descricao(),
                    linha.resultado().rotulo, celula(linha.motivo())));
        }
        md.append("\n<details><summary>Perguntas e respostas</summary>\n\n");
        for (Linha linha : linhas) {
            md.append("### `%s`: %s\n\n".formatted(linha.caso().id(), linha.resultado().rotulo));
            for (MensagemDaConversa mensagem : linha.caso().historico()) {
                md.append("**%s (histórico):**\n\n%s\n".formatted(
                        mensagem.autor() == MensagemDaConversa.Autor.TURMA ? "Turma" : "Personagem",
                        citacao(mensagem.texto())));
            }
            md.append("**Pergunta:**\n\n").append(citacao(linha.caso().pergunta()))
                    .append("**Resposta:**\n\n").append(citacao(linha.resposta()))
                    .append("**Critério:** ").append(linha.caso().criterio()).append("\n\n")
                    .append("**Motivo:** ").append(linha.motivo()).append("\n\n");
        }
        return md.append("</details>\n").toString();
    }

    void gravar() throws IOException {
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, markdown());
    }

    private static String celula(String texto) {
        return texto.replace("|", "\\|").replaceAll("\\s*\\R\\s*", " ");
    }

    private static String citacao(String texto) {
        if (texto == null || texto.isBlank()) {
            return "> (sem resposta)\n\n";
        }
        return texto.strip().lines().map(l -> "> " + l).reduce((a, b) -> a + "\n" + b).orElseThrow() + "\n\n";
    }
}
