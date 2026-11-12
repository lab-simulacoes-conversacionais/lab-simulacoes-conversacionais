package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.AgrupadorDeFrases;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Fica entre o adaptador e a tela durante uma resposta (RN-20, Stack, seção 5, passo 7): agrupa os
 * trechos em frases e modera cada uma antes de exibi-la. A moderação é sequencial, na thread que
 * recebe os trechos, então as frases chegam à tela na ordem.
 *
 * <p>Quem decide o bloqueio é o texto ter sido sinalizado, mesmo sem categoria. Depois do bloqueio, os
 * trechos que ainda chegam são descartados sem moderar: o adaptador continua consumindo a resposta só
 * para gravar os tokens (RNF-13).
 *
 * <p>Uma falha da moderação não interrompe a resposta, porque uma exceção aqui impediria o adaptador de
 * gravar os metadados dos tokens já gastos (RNF-13). Nenhuma frase aparece depois dela, o restante é
 * descartado como no bloqueio e a falha fica em {@link #falha()}, para quem chamou tratá-la como uma
 * falha do provedor quando a resposta terminar.
 */
final class ModeracaoDaResposta {

    /** A frase bloqueada, que vai para o curador e nunca para a tela. */
    record Bloqueio(String frase, List<String> categorias) {}

    private final AdaptadorDeIa adaptador;
    private final Consumer<String> aoAprovar;
    private final Consumer<Bloqueio> aoBloquear;
    private final AgrupadorDeFrases agrupador = new AgrupadorDeFrases();
    private Bloqueio bloqueio;
    private FalhaNoProvedorDeIa falha;

    /**
     * @param aoBloquear chamado uma vez, na primeira frase bloqueada; se lançar exceção, a resposta é
     *     interrompida e o bloqueio não vale
     */
    ModeracaoDaResposta(AdaptadorDeIa adaptador, Consumer<String> aoAprovar, Consumer<Bloqueio> aoBloquear) {
        this.adaptador = adaptador;
        this.aoAprovar = aoAprovar;
        this.aoBloquear = aoBloquear;
    }

    void receber(String trecho) {
        if (parou()) {
            return;
        }
        for (String frase : agrupador.acrescentar(trecho)) {
            if (!moderar(frase)) {
                return;
            }
        }
    }

    /** A resposta terminou: modera e exibe o que sobrou sem pontuação final. */
    void terminar() {
        if (!parou()) {
            agrupador.terminar().ifPresent(this::moderar);
        }
    }

    /** A falha da moderação que fez a resposta parar de ser exibida, se houve. */
    Optional<FalhaNoProvedorDeIa> falha() {
        return Optional.ofNullable(falha);
    }

    private boolean parou() {
        return bloqueio != null || falha != null;
    }

    /** @return se a frase foi aprovada */
    private boolean moderar(String frase) {
        if (frase.isBlank()) {
            return true;
        }
        ResultadoDaModeracao resultado;
        try {
            resultado = adaptador.moderar(frase.strip());
        } catch (FalhaNoProvedorDeIa e) {
            falha = e;
            return false;
        }
        if (resultado.sinalizado()) {
            Bloqueio encontrado = new Bloqueio(frase.strip(), resultado.categorias());
            aoBloquear.accept(encontrado);
            bloqueio = encontrado;
            return false;
        }
        aoAprovar.accept(frase);
        return true;
    }
}
