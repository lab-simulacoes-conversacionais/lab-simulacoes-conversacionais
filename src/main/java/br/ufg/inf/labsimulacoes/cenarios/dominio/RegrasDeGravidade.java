package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Collection;
import java.util.Optional;

/**
 * As regras de gravidade que suspendem o cenário (RN-23), sem banco nem Spring.
 *
 * <p>A suspensão não é guardada como causa: ela é refeita a partir das sinalizações da versão vigente a cada
 * sinalização ou triagem que chega. Assim, o cenário só volta a Aprovado quando nenhum motivo restar, o descarte
 * de uma sinalização que não suspendeu nada não reativa o cenário, e a aprovação de uma versão nova deixa as
 * sinalizações da anterior de fora sem precisar encerrá-las.
 */
public final class RegrasDeGravidade {

    /** RN-23c: três erros moderados confirmados na mesma versão suspendem o cenário. */
    public static final int MODERADOS_QUE_SUSPENDEM = 3;

    private RegrasDeGravidade() {}

    /**
     * Se o cenário deve ficar suspenso pelas sinalizações da versão vigente, e por qual regra. Com mais de um
     * motivo, vale o mais forte: o erro grave confirmado (que só sai com versão nova), depois a sinalização
     * grave do professor e, por fim, os três moderados.
     *
     * <ul>
     *   <li>RN-23a: só a sinalização feita como professor, com gravidade percebida grave e ainda pendente.
     *       Feita como curador, espera a triagem.
     *   <li>RN-23b: a confirmada com gravidade final grave, de qualquer perfil.
     *   <li>RN-23c: as confirmadas com gravidade final moderada, contadas por sinalização. A grave confirmada não
     *       entra na conta, porque já suspende sozinha, e a leve não suspende.
     *   <li>RN-23d: a descartada não conta para nada.
     * </ul>
     *
     * @param daVersao as sinalizações de uma versão só, a vigente
     * @return vazio se nenhuma regra suspende o cenário
     * @throws IllegalArgumentException se vierem sinalizações de versões diferentes: a contagem do RN-23c é por
     *     versão
     */
    public static Optional<MotivoDaSuspensao> motivo(Collection<SinalizacaoDaVersao> daVersao) {
        if (daVersao.stream().map(SinalizacaoDaVersao::versaoId).distinct().count() > 1) {
            throw new IllegalArgumentException("As regras de gravidade contam as sinalizações de uma versão só");
        }
        if (daVersao.stream().anyMatch(RegrasDeGravidade::erroGraveConfirmado)) {
            return Optional.of(MotivoDaSuspensao.ERRO_GRAVE_CONFIRMADO);
        }
        if (daVersao.stream().anyMatch(RegrasDeGravidade::graveDoProfessorPendente)) {
            return Optional.of(MotivoDaSuspensao.SINALIZACAO_GRAVE);
        }
        if (daVersao.stream().filter(RegrasDeGravidade::moderadoConfirmado).count() >= MODERADOS_QUE_SUSPENDEM) {
            return Optional.of(MotivoDaSuspensao.TRES_ERROS_MODERADOS);
        }
        return Optional.empty();
    }

    private static boolean graveDoProfessorPendente(SinalizacaoDaVersao sinalizacao) {
        return sinalizacao.pendente()
                && sinalizacao.perfil() == PerfilDeQuemSinalizou.PROFESSOR
                && sinalizacao.gravidadePercebida() == GravidadeDoErro.GRAVE;
    }

    private static boolean erroGraveConfirmado(SinalizacaoDaVersao sinalizacao) {
        return confirmadaCom(sinalizacao, GravidadeDoErro.GRAVE);
    }

    private static boolean moderadoConfirmado(SinalizacaoDaVersao sinalizacao) {
        return confirmadaCom(sinalizacao, GravidadeDoErro.MODERADA);
    }

    private static boolean confirmadaCom(SinalizacaoDaVersao sinalizacao, GravidadeDoErro gravidade) {
        return sinalizacao.situacao() == SituacaoDaSinalizacao.CONFIRMADA && sinalizacao.gravidadeFinal() == gravidade;
    }
}
