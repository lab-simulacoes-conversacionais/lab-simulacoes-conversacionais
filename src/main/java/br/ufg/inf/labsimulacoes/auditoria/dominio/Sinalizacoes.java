package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.Optional;
import java.util.Set;

/** As sinalizações de erro gravadas (RF-23). */
public interface Sinalizacoes {

    /**
     * Grava a sinalização pendente.
     *
     * @return o id da sinalização, ou vazio se a mesma pessoa já tinha sinalizado a mesma resposta; nada é
     *     gravado nesse caso
     */
    Optional<Long> registrar(Sinalizacao sinalizacao);

    /** Os ids das respostas da sessão que a pessoa já sinalizou, para a tela não oferecer de novo. */
    Set<Long> respostasSinalizadasPor(long sessaoId, long sinalizadoPorId);
}
