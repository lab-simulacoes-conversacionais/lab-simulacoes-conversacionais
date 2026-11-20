package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AcervoDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AprovacaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.DevolucaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.AprovacaoIncompleta;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComentarioObrigatorio;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DecisaoDeCuradoriaNaoPermitida;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Fila do curador (RF-08): os cenários em curadoria, o conteúdo de um deles e a decisão de aprovar ou
 * devolver com comentário (RN-05, RN-07).
 *
 * <p>As rotas /curadoria/** exigem o perfil Curador na configuração de segurança, e os casos de uso
 * conferem de novo. O botão de aprovar nem aparece para quem criou o cenário ou a versão, que também
 * não pode aprovar pelo domínio (RN-36). Na E1 a aprovação não tem checklist nem modo de teste (RN-06);
 * eles chegam com a #33 e a #34.
 */
@Controller
@RequestMapping("/curadoria")
class CuradoriaController {

    /** A fila da E1 é curta; com mais que isto, a tela avisa que há outros cenários esperando. */
    private static final int TAMANHO_DA_FILA = 50;

    private final AcervoDaCuradoria acervo;
    private final AprovacaoDeCenario aprovacao;
    private final DevolucaoDeCenario devolucao;
    private final DescricoesDaBncc descricoes;
    private final UsuarioLogado usuarioLogado;

    CuradoriaController(
            AcervoDaCuradoria acervo,
            AprovacaoDeCenario aprovacao,
            DevolucaoDeCenario devolucao,
            DescricoesDaBncc descricoes,
            UsuarioLogado usuarioLogado) {
        this.acervo = acervo;
        this.aprovacao = aprovacao;
        this.devolucao = devolucao;
        this.descricoes = descricoes;
        this.usuarioLogado = usuarioLogado;
    }

    /**
     * @param cenario o cenário aberto à direita; sem ele, ou se ele já saiu da fila, abre o primeiro
     */
    @GetMapping
    String fila(@RequestParam(required = false) Long cenario, Model modelo) {
        var trecho = acervo.listar(StatusDoCenario.EM_CURADORIA, 0, TAMANHO_DA_FILA);
        List<Cenario> fila = trecho.itens();
        modelo.addAttribute("fila", fila.stream().map(CenariosController.ItemDaLista::de).toList());
        modelo.addAttribute("haMais", trecho.temProxima());
        fila.stream()
                .filter(item -> cenario == null || item.id().equals(cenario))
                .findFirst()
                .or(() -> fila.stream().findFirst())
                .ifPresent(aberto -> abrir(aberto, modelo));
        return "curadoria/fila";
    }

    /**
     * @param itens os itens do checklist marcados (RF-10); sem nenhum marcado, o navegador não manda o
     *     parâmetro
     */
    @PostMapping("/{id}/aprovar")
    String aprovar(
            @PathVariable long id,
            @RequestParam(name = "itens", required = false) List<ItemDoChecklist> itens,
            RedirectAttributes mensagens) {
        List<ItemDoChecklist> marcados = itens == null ? List.of() : itens;
        try {
            Cenario aprovado = aprovacao.aprovar(id, marcados);
            mensagens.addFlashAttribute("sucesso",
                    "“" + aprovado.versao().conteudo().titulo() + "” foi aprovado e já está na biblioteca.");
            return "redirect:/curadoria";
        } catch (AprovacaoIncompleta incompleta) {
            mensagens.addFlashAttribute("erro", oQueFalta(incompleta));
            // O checklist volta como estava, para o curador não marcar tudo de novo.
            mensagens.addFlashAttribute("marcados", marcados.stream().map(ItemDoChecklist::name).toList());
            return "redirect:/curadoria?cenario=" + id;
        } catch (DecisaoDeCuradoriaNaoPermitida | CenarioForaDeCuradoria | CenarioNaoEncontrado erro) {
            return recusado(id, erro, mensagens);
        }
    }

    /** RN-06, na linguagem da tela: os itens que faltam marcar e as perguntas que faltam fazer. */
    private static String oQueFalta(AprovacaoIncompleta incompleta) {
        var faltas = new ArrayList<String>();
        incompleta.itensFaltando().forEach(item -> faltas.add("marcar “" + item.descricao() + "”"));
        int perguntas = incompleta.perguntasFaltando();
        if (perguntas > 0) {
            faltas.add("fazer mais " + perguntas + (perguntas == 1 ? " pergunta" : " perguntas")
                    + " de teste ao personagem");
        }
        return "Para aprovar, falta " + String.join("; ", faltas) + ".";
    }

    @PostMapping("/{id}/devolver")
    String devolver(
            @PathVariable long id,
            @RequestParam(required = false) String comentario,
            RedirectAttributes mensagens) {
        try {
            Cenario devolvido = devolucao.devolver(id, comentario);
            mensagens.addFlashAttribute("sucesso", "“" + devolvido.versao().conteudo().titulo()
                    + "” voltou ao autor como rascunho, com o seu comentário.");
            return "redirect:/curadoria";
        } catch (ComentarioObrigatorio erro) {
            mensagens.addFlashAttribute("erro", "Para devolver, escreva um comentário dizendo o que precisa mudar.");
            mensagens.addFlashAttribute("abrirDevolucao", true);
            return "redirect:/curadoria?cenario=" + id;
        } catch (DecisaoDeCuradoriaNaoPermitida | CenarioForaDeCuradoria | CenarioNaoEncontrado erro) {
            return recusado(id, erro, mensagens);
        }
    }

    private void abrir(Cenario cenario, Model modelo) {
        long eu = usuarioLogado.id();
        modelo.addAttribute("aberto", cenario.id());
        modelo.addAttribute("conteudo",
                ConteudoExibido.de(cenario.versao().conteudo(), cenario.versao().numero(), descricoes));
        // RN-36: nem o autor do cenário nem quem criou a versão aprova, mesmo com o perfil Curador.
        boolean souAutor = cenario.temAutor(eu) || cenario.versao().criadaPorId() == eu;
        modelo.addAttribute("souAutor", souAutor);
        // RF-10, RN-06: o checklist e quantas perguntas de teste o curador já fez nesta versão.
        modelo.addAttribute("itensDoChecklist", ItemDoChecklist.values());
        modelo.addAttribute("minimoDeTeste", PerguntasDeTeste.MINIMO_PARA_APROVAR);
        modelo.addAttribute("perguntasDeTeste", souAutor ? 0 : aprovacao.perguntasDeTesteFeitas(cenario.id()));
    }

    private static String recusado(long id, RuntimeException erro, RedirectAttributes mensagens) {
        String mensagem = switch (erro) {
            case DecisaoDeCuradoriaNaoPermitida naoPermitida -> naoPermitida.getMessage();
            case CenarioForaDeCuradoria foraDaFila -> "Este cenário já foi decidido e saiu da fila.";
            default -> "Este cenário não existe.";
        };
        mensagens.addFlashAttribute("erro", mensagem);
        return "redirect:/curadoria?cenario=" + id;
    }
}
