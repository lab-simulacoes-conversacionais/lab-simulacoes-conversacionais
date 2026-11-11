package br.ufg.inf.labsimulacoes.ia.dominio;

import java.util.Optional;

/**
 * Porta de persistência dos metadados das respostas da IA. A implementação fica na infraestrutura.
 * Não há operação de alterar nem de apagar: os metadados são a trilha de rastreabilidade (RNF-10).
 */
public interface RegistroDeMetadados {

    /** Devolve os metadados com o id gerado. */
    MetadadosDaResposta gravar(MetadadosDaResposta metadados);

    Optional<MetadadosDaResposta> buscar(long id);
}
