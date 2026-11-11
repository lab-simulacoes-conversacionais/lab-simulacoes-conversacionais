package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.session.InvalidSessionStrategy;

/**
 * Leva para o login quem não tem sessão válida.
 *
 * <p>O HTMX seguiria um redirecionamento comum e colocaria a tela de login dentro do fragmento da
 * página, então ele recebe 401 com {@code HX-Redirect}, que faz o navegador trocar de página.
 */
final class SessaoExpirada implements InvalidSessionStrategy, AuthenticationEntryPoint {

    private static final String LOGIN = "/login";

    static boolean ehRequisicaoHtmx(HttpServletRequest requisicao) {
        return "true".equals(requisicao.getHeader("HX-Request"));
    }

    /**
     * O Spring também chama este método quando falta o token CSRF. Com a sessão ainda válida, isso não
     * é expiração, e sim um envio sem token, que precisa ser recusado.
     *
     * <p>Quando a sessão expirou e o próprio destino já é a tela de login, a página é renderizada com
     * uma sessão nova (e um novo token CSRF), em vez de redirecionar, para quem entrar de novo logo
     * após expirar não cair em um 302 sem conteúdo.
     */
    @Override
    public void onInvalidSessionDetected(HttpServletRequest requisicao, HttpServletResponse resposta)
            throws IOException {
        if (requisicao.isRequestedSessionIdValid()) {
            resposta.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else if (ehAberturaDoLogin(requisicao)) {
            encaminharParaLogin(requisicao, resposta);
        } else {
            // A sessão existia e expirou (RNF-09): a tela de login avisa com ?expirada.
            levarAoLogin(requisicao, resposta, true);
        }
    }

    @Override
    public void commence(
            HttpServletRequest requisicao, HttpServletResponse resposta, AuthenticationException erro)
            throws IOException {
        // Quem nunca entrou só é levado ao login, sem o aviso de expiração.
        levarAoLogin(requisicao, resposta, false);
    }

    private static void levarAoLogin(
            HttpServletRequest requisicao, HttpServletResponse resposta, boolean expirada)
            throws IOException {
        String destino = requisicao.getContextPath() + LOGIN + (expirada ? "?expirada" : "");
        if (ehRequisicaoHtmx(requisicao)) {
            resposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resposta.setHeader("HX-Redirect", destino);
        } else {
            resposta.sendRedirect(destino);
        }
    }

    /** Abertura normal da tela de login (GET de navegador), o único caso que deve renderizar a página. */
    private static boolean ehAberturaDoLogin(HttpServletRequest requisicao) {
        return "GET".equals(requisicao.getMethod())
                && LOGIN.equals(requisicao.getServletPath())
                && !ehRequisicaoHtmx(requisicao);
    }

    private static void encaminharParaLogin(HttpServletRequest requisicao, HttpServletResponse resposta)
            throws IOException {
        try {
            requisicao.getRequestDispatcher(LOGIN).forward(requisicao, resposta);
        } catch (ServletException erro) {
            throw new IOException(erro);
        }
    }
}
