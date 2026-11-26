package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.PerfilDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PrazoDaSinalizacao;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Com que perfil a pessoa logada pode sinalizar uma resposta de uma sessão (RN-21, RN-37), num lugar só,
 * para a tela mostrar o botão exatamente quando o caso de uso aceita a sinalização.
 *
 * <ul>
 *   <li>Como professor, na própria sessão, durante ela ou até 7 dias depois.
 *   <li>Senão, como curador, se tiver o perfil, em qualquer sessão e a qualquer momento.
 * </ul>
 */
final class QuemSinaliza {

    private static final String PROFESSOR = "ROLE_PROFESSOR";
    private static final String CURADOR = "ROLE_CURADOR";

    private QuemSinaliza() {}

    /**
     * @param base de onde o prazo da sessão conta ({@link PrazoDaSinalizacao#base})
     * @return vazio se a pessoa não pode sinalizar nesta sessão agora
     */
    static Optional<PerfilDaSinalizacao> perfil(boolean dono, Instant base, Instant agora) {
        if (dono && PrazoDaSinalizacao.permite(base, agora) && tem(PROFESSOR)) {
            return Optional.of(PerfilDaSinalizacao.PROFESSOR);
        }
        if (tem(CURADOR)) {
            return Optional.of(PerfilDaSinalizacao.CURADOR);
        }
        return Optional.empty();
    }

    /** Quem vê a conversa: o professor dono da sessão ou um curador (RN-37). */
    static boolean podeVer(boolean dono) {
        return dono || tem(CURADOR);
    }

    private static boolean tem(String perfil) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null
                && autenticacao.getAuthorities().stream().anyMatch(dado -> perfil.equals(dado.getAuthority()));
    }
}
