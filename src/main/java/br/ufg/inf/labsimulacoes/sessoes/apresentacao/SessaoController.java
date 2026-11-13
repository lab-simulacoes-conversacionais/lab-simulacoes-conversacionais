package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.TurmaDaEscola;
import br.ufg.inf.labsimulacoes.acesso.TurmasDaEscola;
import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ControlesDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.InicioDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.CenarioSemVersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaNaoEncontrada;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Preparação da sessão (RF-14), modo projeção com o painel do professor (RF-15, RF-17) e o resumo
 * depois do encerramento. A pergunta e a resposta do personagem ficam no {@link ConversaController}.
 *
 * <p>Os controles respondem às requisições HTMX com o painel atualizado; reiniciar também limpa a
 * conversa da tela, e encerrar leva ao resumo. Uma recusa vai para a área de avisos, como as da
 * pergunta. Só o professor que iniciou a sessão a vê e controla (RN-10), o que os casos de uso conferem.
 */
@Controller
@RequestMapping("/sessoes")
class SessaoController {

    private static final String PAINEL = "sessoes/painel :: painel";
    private static final int CENARIOS_NA_ESCOLHA = 100;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final InicioDeSessao inicio;
    private final ControlesDaSessao controles;
    private final TurmasDaEscola turmas;
    private final BibliotecaDeCenarios biblioteca;
    private final CenariosAprovados aprovados;

    SessaoController(
            InicioDeSessao inicio,
            ControlesDaSessao controles,
            TurmasDaEscola turmas,
            BibliotecaDeCenarios biblioteca,
            CenariosAprovados aprovados) {
        this.inicio = inicio;
        this.controles = controles;
        this.turmas = turmas;
        this.biblioteca = biblioteca;
        this.aprovados = aprovados;
    }

    // --------------------------------------------------------------------------------------------
    // Preparação (RF-14)
    // --------------------------------------------------------------------------------------------

    /** @param cenario já marcado, quando o professor vem da biblioteca */
    @GetMapping("/nova")
    String nova(@RequestParam(required = false) Long cenario, Model modelo) {
        modelo.addAttribute("turmas", turmas.listar());
        modelo.addAttribute("cenarios", biblioteca.listar(0, CENARIOS_NA_ESCOLHA).cenarios());
        modelo.addAttribute("cenarioEscolhido", cenario);
        return "sessoes/nova";
    }

    @PostMapping
    String iniciar(
            @RequestParam(required = false) Long turma,
            @RequestParam(required = false) Long cenario,
            Model modelo,
            RedirectAttributes mensagens) {
        if (turma == null || cenario == null) {
            mensagens.addFlashAttribute("erro", "Escolha a turma e o cenário para começar.");
            return "redirect:/sessoes/nova" + (cenario == null ? "" : "?cenario=" + cenario);
        }
        try {
            return "redirect:/sessoes/" + inicio.iniciar(turma, cenario).id();
        } catch (TurmaComSessaoEmAndamento erro) {
            mensagens.addFlashAttribute("erro",
                    "Esta turma já tem uma sessão em andamento. Encerre a anterior antes de começar outra.");
        } catch (CenarioSemVersaoAprovada erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não está mais aprovado. Escolha outro da biblioteca.");
        } catch (TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", "Esta turma não existe mais. Escolha outra.");
        }
        return "redirect:/sessoes/nova?cenario=" + cenario;
    }

    // --------------------------------------------------------------------------------------------
    // Modo projeção (RF-15, RF-17)
    // --------------------------------------------------------------------------------------------

    @GetMapping("/{id}")
    String projecao(@PathVariable long id, Model modelo) {
        Sessao sessao = controles.sessao(id);
        if (sessao.status() == StatusDaSessao.ENCERRADA) {
            return "redirect:/sessoes/" + id + "/encerrada";
        }
        modelo.addAttribute("tela", tela(sessao, controles.estado(id)));
        return "sessoes/projecao";
    }

    /** O painel de novo, depois da resposta: o bloqueio pausa a sessão, e a pergunta conta no limite. */
    @GetMapping("/{id}/painel")
    String painel(@PathVariable long id, Model modelo) {
        return comPainel(id, controles.estado(id), modelo);
    }

    @PostMapping("/{id}/pausar")
    String pausar(@PathVariable long id, Model modelo) {
        return comPainel(id, controles.pausar(id), modelo);
    }

    @PostMapping("/{id}/retomar")
    String retomar(@PathVariable long id, Model modelo) {
        return comPainel(id, controles.retomar(id), modelo);
    }

    /** RN-19: limpa a tela, mas perguntas e respostas continuam no registro da sessão. */
    @PostMapping("/{id}/reiniciar")
    String reiniciar(@PathVariable long id, Model modelo) {
        comPainel(id, controles.reiniciar(id), modelo);
        return "sessoes/painel :: reiniciada";
    }

    @PostMapping("/{id}/encerrar")
    String encerrar(@PathVariable long id, HttpServletResponse resposta) {
        controles.encerrar(id);
        resposta.setHeader("HX-Redirect", "/sessoes/" + id + "/encerrada");
        return "sessoes/painel :: vazio";
    }

    @GetMapping("/{id}/encerrada")
    String encerrada(@PathVariable long id, Model modelo) {
        Sessao sessao = controles.sessao(id);
        if (sessao.status() != StatusDaSessao.ENCERRADA) {
            return "redirect:/sessoes/" + id;
        }
        EstadoDaSessao estado = controles.estado(id);
        VersaoAprovada versao = versao(sessao);
        Instant fim = sessao.encerradaEm();
        Duration emPausa = estado.tempoEmPausaAte(fim);
        modelo.addAttribute("versao", versao);
        modelo.addAttribute("turma", nomeDaTurma(sessao.turmaId()));
        modelo.addAttribute("data", DATA.format(sessao.iniciadaEm()));
        modelo.addAttribute("perguntas", estado.perguntasDaTurma());
        modelo.addAttribute("duracao", minutos(Duration.between(sessao.iniciadaEm(), fim).minus(emPausa)));
        modelo.addAttribute("emPausa", minutos(emPausa));
        return "sessoes/encerrada";
    }

    // --------------------------------------------------------------------------------------------
    // Recusas dos controles, na área de avisos da tela
    // --------------------------------------------------------------------------------------------

    @ExceptionHandler(SessaoForaDoEstado.class)
    ModelAndView foraDoEstado(HttpServletResponse resposta) {
        return ConversaController.aviso(resposta, HttpStatus.CONFLICT,
                "A sessão já estava assim. A tela foi atualizada; tente de novo se precisar.");
    }

    @ExceptionHandler(SessaoEncerrada.class)
    ModelAndView sessaoEncerrada(HttpServletResponse resposta) {
        return ConversaController.aviso(resposta, HttpStatus.CONFLICT,
                "A sessão foi encerrada. Para continuar o tema, inicie uma nova sessão.");
    }

    @ExceptionHandler(TurmaComSessaoEmAndamento.class)
    ModelAndView turmaOcupada(HttpServletResponse resposta) {
        return ConversaController.aviso(resposta, HttpStatus.CONFLICT,
                "Outra sessão começou nesta turma durante a pausa. Encerre uma delas para continuar.");
    }

    @ExceptionHandler(SessaoNaoEncontrada.class)
    ModelAndView naoEncontrada(HttpServletResponse resposta) {
        return ConversaController.aviso(resposta, HttpStatus.NOT_FOUND, "Sessão não encontrada.");
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    private String comPainel(long id, EstadoDaSessao estado, Model modelo) {
        modelo.addAttribute("tela", tela(controles.sessao(id), estado));
        return PAINEL;
    }

    private TelaDaSessao tela(Sessao sessao, EstadoDaSessao estado) {
        return TelaDaSessao.de(sessao, estado, versao(sessao), nomeDaTurma(sessao.turmaId()), Instant.now());
    }

    /** A sessão guarda a versão com que começou (RN-08), que continua legível mesmo se o cenário mudar. */
    private VersaoAprovada versao(Sessao sessao) {
        return aprovados.versaoAprovada(sessao.versaoId())
                .orElseThrow(() -> new IllegalStateException("A sessão " + sessao.id() + " aponta para uma versão não aprovada"));
    }

    private String nomeDaTurma(long turmaId) {
        return turmas.buscar(turmaId).map(TurmaDaEscola::rotulo).orElse("Turma removida");
    }

    private static String minutos(Duration duracao) {
        long total = Math.max(0, duracao.toMinutes());
        return total < 1 ? "menos de 1 min" : total + " min";
    }
}
