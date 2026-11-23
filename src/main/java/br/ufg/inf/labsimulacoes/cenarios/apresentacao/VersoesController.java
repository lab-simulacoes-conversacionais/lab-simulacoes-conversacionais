package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AutoriaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.VersoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoPermitida;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Histórico, comparação e restauração de versões de um cenário (RF-11, RN-08): a lista das versões e
 * duas delas lado a lado, com as diferenças destacadas.
 *
 * <p>O professor vê o histórico dos próprios cenários, e o curador vê o de todos (RN-37); os casos de
 * uso conferem, e quem não pode ver recebe 403. Criar uma versão nova e restaurar uma versão aprovada são
 * do autor: a versão nova abre em rascunho no editor e passa pela curadoria de sempre, enquanto a vigente
 * continua em sala.
 */
@Controller
@RequestMapping("/cenarios/{id}/versoes")
class VersoesController {

    private final VersoesDoCenario versoes;
    private final AutoriaDeCenarios autoria;
    private final UsuarioLogado usuarioLogado;

    VersoesController(VersoesDoCenario versoes, AutoriaDeCenarios autoria, UsuarioLogado usuarioLogado) {
        this.versoes = versoes;
        this.autoria = autoria;
        this.usuarioLogado = usuarioLogado;
    }

    /**
     * @param de uma das versões comparadas; sem ela, a comparação mostra a mais nova e a anterior aprovada
     * @param para a outra versão; a de número menor fica sempre à esquerda
     */
    @GetMapping
    String historico(
            @PathVariable long id,
            @RequestParam(required = false) Integer de,
            @RequestParam(required = false) Integer para,
            Model modelo,
            RedirectAttributes mensagens) {
        Cenario cenario;
        try {
            cenario = autoria.buscar(id);
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
            return "redirect:/cenarios";
        }
        boolean souAutor = cenario.temAutor(usuarioLogado.id());
        var historico = HistoricoExibido.de(versoes.historico(id), cenario, souAutor);
        modelo.addAttribute("cenario", CenariosController.ItemDaLista.de(cenario));
        modelo.addAttribute("historico", historico);
        modelo.addAttribute("souAutor", souAutor);
        modelo.addAttribute("voltar", voltar(cenario, souAutor));

        if (historico.temComparacao()) {
            boolean escolheu = de != null && para != null;
            int antiga = escolheu ? de : historico.antigaPadrao();
            int nova = escolheu ? para : historico.novaPadrao();
            if (antiga == nova) {
                modelo.addAttribute("avisoDaComparacao", "Escolha duas versões diferentes para comparar.");
                antiga = historico.antigaPadrao();
                nova = historico.novaPadrao();
            }
            try {
                modelo.addAttribute("comparacao", ComparacaoExibida.de(versoes.comparar(id, antiga, nova)));
            } catch (VersaoNaoEncontrada erro) {
                modelo.addAttribute("avisoDaComparacao", "Este cenário não tem a versão pedida. Escolha outra.");
                modelo.addAttribute("comparacao", ComparacaoExibida.de(
                        versoes.comparar(id, historico.antigaPadrao(), historico.novaPadrao())));
            }
        }
        return "cenarios/versoes";
    }

    /** RN-08: editar um cenário aprovado abre a versão seguinte em rascunho, com o conteúdo da vigente. */
    @PostMapping("/nova")
    String criarNova(@PathVariable long id, RedirectAttributes mensagens) {
        try {
            Cenario comNova = versoes.criarNovaVersao(id);
            mensagens.addFlashAttribute("sucesso", "Versão " + comNova.versao().numero()
                    + " criada em rascunho. A versão em sala continua valendo até esta ser aprovada.");
            return "redirect:/cenarios/" + id + "/editar";
        } catch (VersaoNaoPermitida erro) {
            mensagens.addFlashAttribute("erro", "Não dá para criar uma versão nova agora: o cenário precisa estar"
                    + " aprovado ou suspenso, sem outra versão em rascunho ou em curadoria.");
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
            return "redirect:/cenarios";
        }
        return "redirect:/cenarios/" + id + "/versoes";
    }

    /** RF-11: a versão aprovada escolhida volta como a versão seguinte, em rascunho. */
    @PostMapping("/{numero}/restaurar")
    String restaurar(@PathVariable long id, @PathVariable int numero, RedirectAttributes mensagens) {
        try {
            Cenario restaurado = versoes.restaurar(id, numero);
            mensagens.addFlashAttribute("sucesso", "A versão " + numero + " voltou como versão "
                    + restaurado.versao().numero() + ", em rascunho. Revise e envie à curadoria; a versão em sala"
                    + " continua valendo até esta ser aprovada.");
            return "redirect:/cenarios/" + id + "/editar";
        } catch (VersaoNaoPermitida erro) {
            mensagens.addFlashAttribute("erro", "A versão " + numero + " não pode ser restaurada agora. Só se"
                    + " restaura uma versão aprovada anterior, e sem outra versão em rascunho ou em curadoria.");
        } catch (VersaoNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não tem a versão " + numero + ".");
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
            return "redirect:/cenarios";
        }
        return "redirect:/cenarios/" + id + "/versoes";
    }

    /**
     * Para onde o "Voltar" leva: o autor volta aos seus cenários; o curador, à fila se a versão mais nova
     * está em curadoria, ao detalhe da biblioteca se o cenário está nela, ou à fila.
     */
    private static Voltar voltar(Cenario cenario, boolean souAutor) {
        if (souAutor) {
            return new Voltar("/cenarios", "Voltar aos meus cenários");
        }
        if (cenario.versao().status() == StatusDaVersao.EM_CURADORIA) {
            return new Voltar("/curadoria?cenario=" + cenario.id(), "Voltar à fila do curador");
        }
        if (cenario.status() == StatusDoCenario.APROVADO) {
            return new Voltar("/biblioteca/" + cenario.id(), "Voltar ao cenário");
        }
        return new Voltar("/curadoria", "Voltar à fila do curador");
    }

    record Voltar(String endereco, String texto) {}
}
