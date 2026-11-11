package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Leituras comuns aos casos de uso da sessão em sala. Não verifica perfil: quem a usa é que é o caso de
 * uso, com o {@code @PreAuthorize}.
 */
@Component
class LeituraDaSessao {

    private final Sessoes sessoes;
    private final UsuarioLogado usuarioLogado;

    LeituraDaSessao(Sessoes sessoes, UsuarioLogado usuarioLogado) {
        this.sessoes = sessoes;
        this.usuarioLogado = usuarioLogado;
    }

    /** RN-10: a sessão é ligada ao professor que a iniciou, e só ele a vê e controla. */
    Sessao doProfessorLogado(Optional<Sessao> encontrada, long id) {
        Sessao sessao = encontrada.orElseThrow(() -> new SessaoNaoEncontrada(id));
        if (!sessao.conduzidaPor(usuarioLogado.id())) {
            throw new AccessDeniedException("Só o professor que iniciou a sessão a controla");
        }
        return sessao;
    }

    /**
     * O estado refeito do PostgreSQL. Os controles sempre partem daqui, e não do Redis: o Redis é
     * gravado depois da confirmação, quando o lock do {@code FOR UPDATE} já foi liberado, então o
     * controle seguinte pode ler a chave antes de ela ser atualizada.
     */
    EstadoDaSessao doBanco(long id) {
        return EstadoDaSessao.reconstruir(sessoes
                .historico(id, EstadoDaSessao.MENSAGENS_GUARDADAS)
                .orElseThrow(() -> new SessaoNaoEncontrada(id)));
    }
}
