package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * O que o professor ou o curador informa para a IA sugerir um cenário (RF-07): tema, componente, ano e
 * habilidades BNCC. A etapa é opcional. Tudo é conferido antes da chamada ao provedor, para um pedido
 * incompleto não gastar tokens.
 */
public record PedidoDeSugestaoDeCenario(String tema, VinculoCurricular curriculo) {

    public PedidoDeSugestaoDeCenario {
        tema = Textos.opcional(tema);
        if (tema == null) {
            throw new IllegalArgumentException("O tema da sugestão é obrigatório");
        }
        if (curriculo == null || curriculo.componente() == null) {
            throw new IllegalArgumentException("O componente curricular da sugestão é obrigatório");
        }
        if (curriculo.ano() == null) {
            throw new IllegalArgumentException("O ano da sugestão é obrigatório");
        }
        if (curriculo.habilidades().isEmpty()) {
            throw new IllegalArgumentException("A sugestão precisa de ao menos uma habilidade BNCC");
        }
    }

    /**
     * Monta o rascunho com o que a IA devolveu (RN-04). O vínculo com a BNCC vem do pedido, e não da IA,
     * porque já foi conferido no catálogo. Sem título, o rascunho usa o tema, já que precisa de um para
     * aparecer na lista. Uma duração fora de 10 a 30 minutos fica em branco para o curador preencher
     * (RN-17), em vez de recusar a sugestão inteira.
     */
    public ConteudoDoCenario conteudoSugerido(
            String titulo, RoteiroDoCenario roteiro, String faixaEtaria, Integer duracaoPrevistaMin) {
        String tituloDoRascunho = Textos.opcional(titulo) == null ? tema : titulo;
        Integer duracao = duracaoPrevistaMin != null
                        && duracaoPrevistaMin >= ConteudoDoCenario.DURACAO_MINIMA_MIN
                        && duracaoPrevistaMin <= ConteudoDoCenario.DURACAO_MAXIMA_MIN
                ? duracaoPrevistaMin
                : null;
        return new ConteudoDoCenario(tituloDoRascunho, roteiro, curriculo, faixaEtaria, duracao);
    }
}
