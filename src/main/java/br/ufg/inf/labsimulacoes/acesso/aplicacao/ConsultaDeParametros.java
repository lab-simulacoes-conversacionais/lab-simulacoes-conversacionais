package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.ParametrosDeUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.RegistroDeParametros;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura dos parâmetros pelos outros módulos. Fica separada da {@link GestaoDeParametros}, que é só
 * do administrador, porque quem lê é, por exemplo, o professor ao iniciar uma sessão.
 */
@Service
class ConsultaDeParametros implements ParametrosDeUso {

    private final RegistroDeParametros registro;

    ConsultaDeParametros(RegistroDeParametros registro) {
        this.registro = registro;
    }

    @Override
    @Transactional(readOnly = true)
    public int limiteDePerguntasPorSessao() {
        return registro.carregar().limiteDePerguntasPorSessao();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> custoDiarioMaximoUsd() {
        return registro.carregar().custoDiarioMaximo();
    }
}
