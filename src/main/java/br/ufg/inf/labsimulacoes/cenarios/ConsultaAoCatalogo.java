package br.ufg.inf.labsimulacoes.cenarios;

/**
 * Filtros de uma busca no {@link CatalogoBncc}. Todos são opcionais e se combinam; o vazio é ignorado.
 *
 * <pre>{@code
 * catalogo.buscar(ConsultaAoCatalogo.todas().comComponente("História").comAno(8));
 * }</pre>
 *
 * @param codigo código igual a este, sem diferenciar maiúsculas
 * @param descricao descrição que contém este trecho, sem diferenciar maiúsculas
 * @param etapa etapa igual a esta, como EF
 * @param ano habilidade que vale para este ano, de 1 a 9; 7 encontra também as do bloco 6º e 7º
 * @param componente nome do componente igual a este, sem diferenciar maiúsculas
 * @param limite número máximo de resultados; {@code null} usa o padrão, que é 50
 */
public record ConsultaAoCatalogo(
        String codigo, String descricao, String etapa, Integer ano, String componente, Integer limite) {

    public static ConsultaAoCatalogo todas() {
        return new ConsultaAoCatalogo(null, null, null, null, null, null);
    }

    public static ConsultaAoCatalogo porCodigo(String codigo) {
        return todas().comCodigo(codigo);
    }

    public ConsultaAoCatalogo comCodigo(String codigo) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }

    public ConsultaAoCatalogo comDescricao(String descricao) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }

    public ConsultaAoCatalogo comEtapa(String etapa) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }

    public ConsultaAoCatalogo comAno(Integer ano) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }

    public ConsultaAoCatalogo comComponente(String componente) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }

    public ConsultaAoCatalogo comLimite(Integer limite) {
        return new ConsultaAoCatalogo(codigo, descricao, etapa, ano, componente, limite);
    }
}
