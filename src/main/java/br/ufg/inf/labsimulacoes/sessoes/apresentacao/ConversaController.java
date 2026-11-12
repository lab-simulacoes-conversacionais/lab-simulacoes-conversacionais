package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ControlesDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ConversaDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.PerguntaRecebida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.LimiteDePerguntasAtingido;
import br.ufg.inf.labsimulacoes.sessoes.dominio.PerguntaSemResposta;
import br.ufg.inf.labsimulacoes.sessoes.dominio.RespostaEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.HtmlUtils;

/**
 * Pergunta da turma pelo painel do professor e resposta do personagem em trechos na tela de projeção
 * (RF-16, Stack, seção 5, passos 1 e 6). A pergunta vai por uma requisição HTMX; a resposta, pelo canal
 * SSE que o fragmento devolvido abre.
 *
 * <p>Uma recusa da pergunta volta com o status HTTP do motivo e o aviso no corpo, com os cabeçalhos do
 * HTMX que o colocam na área de avisos da tela ({@value #AREA_DE_AVISOS}).
 */
@Controller
@RequestMapping("/sessoes/{id}")
class ConversaController {

    private static final Logger LOG = LoggerFactory.getLogger(ConversaController.class);

    static final String AREA_DE_AVISOS = "#aviso-da-sessao";

    /** Bem acima dos 10 s da resposta completa (RNF-03), para só cortar uma resposta travada. */
    private static final Duration TEMPO_MAXIMO_DA_RESPOSTA = Duration.ofSeconds(60);

    /** A pergunta continua pendente: tentar de novo é abrir o canal da resposta outra vez (RNF-04). */
    private static final String AVISO_DE_FALHA =
            "Não foi possível obter a resposta do personagem. Tente de novo daqui a pouco.";

    private final ConversaDaSessao conversa;
    private final ControlesDaSessao controles;

    /**
     * A resposta é gerada fora da thread da requisição, para o SSE mandar cada trecho assim que chega.
     * As threads virtuais esperam o provedor sem ocupar o pool do servidor, e o contexto de segurança
     * vai junto, porque os casos de uso conferem o perfil (RN-10, RN-37).
     */
    private final Executor respostas;

    ConversaController(ConversaDaSessao conversa, ControlesDaSessao controles) {
        this.conversa = conversa;
        this.controles = controles;
        var threads = new SimpleAsyncTaskExecutor("resposta-do-personagem-");
        threads.setVirtualThreads(true);
        this.respostas = new DelegatingSecurityContextExecutor(threads);
    }

    @PostMapping("/perguntas")
    String enviarPergunta(@PathVariable long id, @RequestParam(defaultValue = "") String texto, Model model) {
        PerguntaRecebida recebida = conversa.enviarPergunta(id, texto);
        model.addAttribute("sessaoId", id);
        model.addAttribute("recebida", recebida);
        return "sessoes/conversa :: pergunta";
    }

    /**
     * Abre o canal e responde à pergunta pendente. Sessão e professor são conferidos antes, para uma
     * recusa voltar como status HTTP e não como evento.
     */
    @GetMapping(path = "/resposta", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    SseEmitter resposta(@PathVariable long id) {
        controles.estado(id);
        var canal = new SseEmitter(TEMPO_MAXIMO_DA_RESPOSTA.toMillis());
        respostas.execute(() -> transmitir(id, canal));
        return canal;
    }

    private void transmitir(long id, SseEmitter canal) {
        try {
            conversa.responderPendente(id, trecho -> enviar(canal, "trecho", escapar(trecho)));
        } catch (FalhaNoProvedorDeIa e) {
            LOG.warn("O provedor de IA falhou na sessão {}", id, e);
            enviarSemFalhar(canal, "falha", escapar(AVISO_DE_FALHA));
        } catch (RespostaEmAndamento e) {
            // A tela reconectou enquanto a resposta anterior ainda chegava: o canal antigo segue com ela.
            LOG.debug("Canal repetido na sessão {}", id);
        } catch (UncheckedIOException e) {
            // A tela fechou no meio da resposta; o streaming do provedor já foi cancelado.
            LOG.info("A tela da sessão {} fechou antes do fim da resposta", id);
            canal.completeWithError(e);
            return;
        } catch (RuntimeException e) {
            LOG.error("Falha ao responder na sessão {}", id, e);
            enviarSemFalhar(canal, "falha", escapar(AVISO_DE_FALHA));
        }
        enviarSemFalhar(canal, "fim", "");
        canal.complete();
    }

    /**
     * A extensão SSE do HTMX insere os dados como HTML, então o texto do modelo é escapado. Com o UTF-8,
     * só os caracteres especiais do HTML mudam, e os acentos passam como estão.
     */
    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
    }

    private static void enviar(SseEmitter canal, String evento, String dados) {
        try {
            canal.send(SseEmitter.event().name(evento).data(dados));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void enviarSemFalhar(SseEmitter canal, String evento, String dados) {
        try {
            enviar(canal, evento, dados);
        } catch (UncheckedIOException | IllegalStateException e) {
            LOG.debug("Não deu para enviar o evento {}: o canal já fechou", evento);
        }
    }

    /** RN-18: o envio fica bloqueado e o sistema sugere passar ao quiz. */
    @ExceptionHandler(LimiteDePerguntasAtingido.class)
    ModelAndView limiteAtingido(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.CONFLICT,
                "A turma já fez todas as perguntas desta sessão. Que tal passar ao quiz?");
    }

    @ExceptionHandler(SessaoForaDoEstado.class)
    ModelAndView sessaoPausada(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.CONFLICT, "A sessão está pausada. Retome a sessão para continuar a conversa.");
    }

    @ExceptionHandler(SessaoEncerrada.class)
    ModelAndView sessaoEncerrada(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.CONFLICT,
                "A sessão foi encerrada. Para continuar o tema, inicie uma nova sessão.");
    }

    @ExceptionHandler(PerguntaSemResposta.class)
    ModelAndView perguntaSemResposta(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.CONFLICT,
                "A pergunta anterior ainda não foi respondida. Aguarde a resposta do personagem.");
    }

    /** A única recusa por argumento é a pergunta vazia; a mensagem é fixa para não expor detalhes internos. */
    @ExceptionHandler(IllegalArgumentException.class)
    ModelAndView perguntaVazia(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.BAD_REQUEST, "Digite a pergunta da turma.");
    }

    @ExceptionHandler(SessaoNaoEncontrada.class)
    ModelAndView sessaoNaoEncontrada(HttpServletResponse resposta) {
        return aviso(resposta, HttpStatus.NOT_FOUND, "Sessão não encontrada.");
    }

    private static ModelAndView aviso(HttpServletResponse resposta, HttpStatus status, String texto) {
        resposta.setHeader("HX-Retarget", AREA_DE_AVISOS);
        resposta.setHeader("HX-Reswap", "innerHTML");
        var aviso = new ModelAndView("sessoes/conversa :: aviso", status);
        aviso.addObject("aviso", texto);
        return aviso;
    }
}
