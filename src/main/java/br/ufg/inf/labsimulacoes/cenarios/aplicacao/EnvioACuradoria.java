package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O autor envia o cenário à curadoria (RF-08). O curador também é autor quando cria cenários (RF-06),
 * então os dois perfis enviam; quem não é autor é recusado pelo próprio cenário.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class EnvioACuradoria {

    private final Cenarios cenarios;
    private final UsuarioLogado usuarioLogado;

    EnvioACuradoria(Cenarios cenarios, UsuarioLogado usuarioLogado) {
        this.cenarios = cenarios;
        this.usuarioLogado = usuarioLogado;
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.EnvioNaoPermitido se quem envia não for o autor
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioIncompleto se faltar algo do RN-02 ou do RN-03
     */
    @Transactional
    public Cenario enviar(long id) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        return cenarios.salvar(cenario.enviarACuradoria(usuarioLogado.id()));
    }
}
