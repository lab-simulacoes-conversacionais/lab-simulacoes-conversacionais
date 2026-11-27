package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadeFinal;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Uma linha do log como a tela mostra (RF-26): a ação em palavras, o que ela afetou, quem fez e quando. Os
 * detalhes gravados são lidos só para a descrição; o comentário da devolução e os motivos ficam de fora,
 * para a tabela continuar curta.
 *
 * @param detalhe o que foi afetado, como "Cenário nº 12" ou "Sinalização nº 3 · gravidade final Grave"
 * @param quando como "14 jun 2026 · 10:42"
 * @param destaque suspensão e erro grave confirmado, que a tabela marca
 */
record LinhaDoLog(String acao, String detalhe, String usuario, String quando, boolean destaque) {

    static LinhaDoLog de(RegistroDeAuditoria registro, Map<Long, String> nomes, DatasNaTela datas) {
        Map<String, Object> detalhes = registro.detalhes();
        String usuario = nomes.getOrDefault(registro.usuarioId(), "Usuário nº " + registro.usuarioId());
        String quando = datas.completo(registro.ocorridoEm());
        String entidade = registro.entidadeId();
        AcaoCritica acao = registro.acao();
        String rotulo = rotuloDoFiltro(acao);
        return switch (acao) {
            case APROVAR_CENARIO, DEVOLVER_CENARIO, REATIVAR_CENARIO, ARQUIVAR_CENARIO ->
                    new LinhaDoLog(rotulo, cenario(entidade), usuario, quando, false);
            case SUSPENDER_CENARIO -> new LinhaDoLog(rotulo, cenario(entidade), usuario, quando, true);
            case TRIAR_SINALIZACAO -> triagem(entidade, detalhes, usuario, quando);
            case ALTERAR_PERFIS -> new LinhaDoLog(rotulo, perfis(entidade, detalhes, nomes), usuario, quando, false);
            case EXPORTAR_RELATORIO -> new LinhaDoLog(rotulo, formato(detalhes), usuario, quando, false);
        };
    }

    /** A ação em palavras. Na triagem, a linha diz se foi confirmada ou descartada; o filtro junta as duas. */
    static String rotuloDoFiltro(AcaoCritica acao) {
        return switch (acao) {
            case APROVAR_CENARIO -> "Cenário aprovado";
            case DEVOLVER_CENARIO -> "Cenário devolvido ao autor";
            case SUSPENDER_CENARIO -> "Cenário suspenso";
            case REATIVAR_CENARIO -> "Cenário reativado";
            case ARQUIVAR_CENARIO -> "Cenário arquivado";
            case TRIAR_SINALIZACAO -> "Sinalização triada";
            case ALTERAR_PERFIS -> "Perfis alterados";
            case EXPORTAR_RELATORIO -> "Relatório exportado";
        };
    }

    private static LinhaDoLog triagem(String entidade, Map<String, Object> detalhes, String usuario, String quando) {
        boolean confirmada = "CONFIRMADA".equals(detalhes.get("decisao"));
        var partes = new ArrayList<String>();
        partes.add(entidade == null ? "Sinalização" : "Sinalização nº " + entidade);
        if (confirmada && detalhes.get("gravidadeFinal") instanceof String gravidade) {
            partes.add("gravidade final " + rotuloDaGravidade(gravidade));
        }
        if (Boolean.TRUE.equals(detalhes.get("foraDoPrazo"))) {
            partes.add("fora do prazo");
        }
        return new LinhaDoLog(
                confirmada ? "Sinalização confirmada" : "Sinalização descartada",
                String.join(" · ", partes), usuario, quando,
                confirmada && "GRAVE".equals(detalhes.get("gravidadeFinal")));
    }

    private static String cenario(String entidade) {
        return entidade == null ? "" : "Cenário nº " + entidade;
    }

    /** "Ana Souza: Professor → Professor, Curador Pedagógico". */
    private static String perfis(String entidade, Map<String, Object> detalhes, Map<Long, String> nomes) {
        String quem = entidade == null ? "Usuário" : nomeDe(entidade, nomes);
        return quem + ": " + listaDePerfis(detalhes.get("perfisAnteriores"))
                + " → " + listaDePerfis(detalhes.get("perfisNovos"));
    }

    private static String nomeDe(String entidade, Map<Long, String> nomes) {
        try {
            long id = Long.parseLong(entidade);
            return nomes.getOrDefault(id, "Usuário nº " + id);
        } catch (NumberFormatException erro) {
            return "Usuário " + entidade;
        }
    }

    private static String listaDePerfis(Object valor) {
        if (!(valor instanceof Collection<?> perfis) || perfis.isEmpty()) {
            return "nenhum perfil";
        }
        return perfis.stream().map(String::valueOf).map(LinhaDoLog::rotuloDoPerfil).collect(Collectors.joining(", "));
    }

    private static String rotuloDoPerfil(String perfil) {
        return switch (perfil) {
            case "PROFESSOR" -> "Professor";
            case "CURADOR" -> "Curador Pedagógico";
            case "ADMINISTRADOR" -> "Administrador";
            default -> perfil;
        };
    }

    private static String rotuloDaGravidade(String gravidade) {
        for (GravidadeFinal opcao : GravidadeFinal.values()) {
            if (opcao.name().equals(gravidade)) {
                return opcao.rotulo();
            }
        }
        return gravidade;
    }

    private static String formato(Map<String, Object> detalhes) {
        return detalhes.get("formato") instanceof String formato ? "Formato " + formato : "";
    }
}
