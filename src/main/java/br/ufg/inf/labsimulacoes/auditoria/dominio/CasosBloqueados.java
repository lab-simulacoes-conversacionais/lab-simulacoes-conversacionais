package br.ufg.inf.labsimulacoes.auditoria.dominio;

/** Porta de persistência dos casos de conteúdo bloqueado, a fila do curador (RN-20, RF-24). */
public interface CasosBloqueados {

    /**
     * Abre o caso com o status {@link CasoBloqueado#ABERTO}. Um caso da mesma sessão no mesmo momento já
     * aberto não se repete, para um evento reprocessado não duplicar a fila; se ele ainda não tiver os
     * metadados da resposta e o novo tiver, ganha os metadados. Assim, a ordem em que chegam o bloqueio
     * e a resposta terminada não importa.
     *
     * @return se o caso foi aberto ou ganhou os metadados agora
     */
    boolean abrir(CasoBloqueado caso);
}
