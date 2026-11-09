package br.ufg.inf.labsimulacoes.cenarios;

import java.util.List;

/**
 * Busca no catálogo de habilidades da BNCC (RF-05), para vincular habilidades aos cenários (RN-02) e
 * para os outros módulos.
 *
 * <p>Exige usuário autenticado, com qualquer perfil. O resultado vem de um cache no Redis, limpo a
 * cada importação do catálogo.
 */
public interface CatalogoBncc {

    /**
     * Habilidades que atendem a todos os filtros preenchidos da consulta, em ordem de código.
     *
     * @throws IllegalArgumentException se o ano não estiver entre 1 e 9 ou o limite for inválido
     */
    List<HabilidadeDoCatalogo> buscar(ConsultaAoCatalogo consulta);
}
