package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.sessoes.SessaoParaRevisao;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Uma sessão como a revisão e as sessões recentes mostram, com as datas no fuso da escola.
 *
 * @param data o dia da aula, como "20 de novembro de 2026"
 * @param limite até quando o professor pode sinalizar, como "27/11/2026 às 10:50"
 */
public record SessaoNaTela(long id, String titulo, String turma, String data, String limite, boolean encerrada) {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Locale PORTUGUES = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PORTUGUES).withZone(FUSO);
    private static final DateTimeFormatter MOMENTO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PORTUGUES).withZone(FUSO);

    static SessaoNaTela de(SessaoParaRevisao sessao, Instant limite) {
        return new SessaoNaTela(
                sessao.id(), sessao.tituloDoCenario(), sessao.turma(), DATA.format(sessao.iniciadaEm()),
                momento(limite), sessao.encerrada());
    }

    static String momento(Instant instante) {
        return MOMENTO.format(instante);
    }
}
