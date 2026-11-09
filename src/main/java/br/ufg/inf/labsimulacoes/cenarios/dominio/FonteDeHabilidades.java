package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Arquivo oficial de uma etapa da BNCC. Cada etapa tem o seu leitor, que conhece o formato do
 * arquivo; incluir outra etapa é criar outro leitor, sem mudar a importação.
 */
public interface FonteDeHabilidades {

    /**
     * Lê todas as habilidades antes de devolver qualquer uma, para que um arquivo com problema não
     * chegue a gravar uma parte do catálogo.
     *
     * @throws CatalogoInvalido se o arquivo não estiver no formato esperado
     */
    LeituraDeHabilidades ler();
}
