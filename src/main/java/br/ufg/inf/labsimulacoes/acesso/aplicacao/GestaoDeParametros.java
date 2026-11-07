package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Parametros;
import br.ufg.inf.labsimulacoes.acesso.dominio.RegistroDeParametros;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Definição dos parâmetros de uso pelo administrador (RF-04, RN-37). */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class GestaoDeParametros {

    private final RegistroDeParametros registro;

    GestaoDeParametros(RegistroDeParametros registro) {
        this.registro = registro;
    }

    @Transactional(readOnly = true)
    public Parametros consultar() {
        return registro.carregar();
    }

    /**
     * Vale para as sessões iniciadas depois da alteração; as que já estão em andamento continuam com
     * o limite que tinham ao começar (ver {@link br.ufg.inf.labsimulacoes.acesso.ParametrosDeUso}).
     *
     * @param custoDiarioMaximoUsd {@code null} para deixar sem custo máximo
     */
    @Transactional
    public Parametros alterar(int limiteDePerguntasPorSessao, BigDecimal custoDiarioMaximoUsd) {
        var parametros = new Parametros(limiteDePerguntasPorSessao, custoDiarioMaximoUsd);
        registro.salvar(parametros);
        return parametros;
    }
}
