package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.Optional;

/** As sinalizações de erro gravadas (RF-23). */
public interface Sinalizacoes {

    /**
     * Grava a sinalização pendente.
     *
     * @return o id da sinalização, ou vazio se a mesma pessoa já tinha sinalizado a mesma resposta; nada é
     *     gravado nesse caso
     */
    Optional<Long> registrar(Sinalizacao sinalizacao);
}
