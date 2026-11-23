package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidoDeSugestaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.List;

/**
 * O pedido de sugestão de cenário como chega da tela (RF-07): tema, componente, ano e habilidades BNCC.
 * A etapa é sempre o Ensino Fundamental, como no editor. Volta preenchido quando o pedido é recusado,
 * para quem pediu não digitar tudo de novo.
 */
record FormularioDaSugestao(String tema, String componente, Integer ano, List<String> habilidades) {

    FormularioDaSugestao {
        habilidades = habilidades == null ? List.of() : List.copyOf(habilidades);
    }

    static FormularioDaSugestao vazio() {
        return new FormularioDaSugestao(null, null, null, List.of());
    }

    /** @throws IllegalArgumentException com a mensagem para quem pediu, se faltar algo ou o tema for longo */
    PedidoDeSugestaoDeCenario paraPedido() {
        return new PedidoDeSugestaoDeCenario(
                tema, new VinculoCurricular(componente, FormularioDoCenario.ETAPA, ano, habilidades));
    }
}
