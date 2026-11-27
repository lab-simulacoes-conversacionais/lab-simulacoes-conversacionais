package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoRegistrada;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoTriada;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Recebe as sinalizações e as triagens da Auditoria e aplica as regras de gravidade (RF-25, RN-23). Cenários
 * não depende da Auditoria: tudo chega pelos eventos, só com ids e códigos.
 *
 * <p>Cada ouvinte roda depois da transação de quem publicou, em outra transação e sem usuário logado. Se a
 * gravação falhar, o evento continua pendente no registro de publicação e é reprocessado quando a aplicação
 * reiniciar; nessa reentrega, a triagem pode chegar antes do registro, e as duas ordens dão o mesmo resultado.
 */
@Component
class OuvinteDeSinalizacoes {

    private final SuspensaoAutomatica suspensao;

    OuvinteDeSinalizacoes(SuspensaoAutomatica suspensao) {
        this.suspensao = suspensao;
    }

    @ApplicationModuleListener
    void sinalizacaoRegistrada(SinalizacaoRegistrada evento) {
        suspensao.registrar(
                SinalizacaoDaVersao.pendente(
                        evento.sinalizacaoId(),
                        evento.versaoId(),
                        PerfilDeQuemSinalizou.valueOf(evento.perfil()),
                        GravidadeDoErro.valueOf(evento.gravidadePercebida()),
                        evento.ocorridoEm()),
                evento.sinalizadoPorId());
    }

    /** Se o registro ainda não chegou, a sinalização é guardada com o momento da triagem. */
    @ApplicationModuleListener
    void sinalizacaoTriada(SinalizacaoTriada evento) {
        PerfilDeQuemSinalizou perfil = PerfilDeQuemSinalizou.valueOf(evento.perfil());
        GravidadeDoErro percebida = GravidadeDoErro.valueOf(evento.gravidadePercebida());
        suspensao.triar(evento.confirmada()
                ? SinalizacaoDaVersao.confirmada(
                        evento.sinalizacaoId(),
                        evento.versaoId(),
                        perfil,
                        percebida,
                        GravidadeDoErro.valueOf(evento.gravidadeFinal()),
                        evento.triadaPorId(),
                        evento.ocorridoEm(),
                        evento.ocorridoEm())
                : SinalizacaoDaVersao.descartada(
                        evento.sinalizacaoId(),
                        evento.versaoId(),
                        perfil,
                        percebida,
                        evento.triadaPorId(),
                        evento.ocorridoEm(),
                        evento.ocorridoEm()));
    }
}
