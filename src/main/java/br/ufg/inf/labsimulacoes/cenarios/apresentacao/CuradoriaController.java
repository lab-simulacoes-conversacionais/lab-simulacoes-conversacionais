package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AcervoDaCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AprovacaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.ArquivamentoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.DevolucaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.TesteDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.VersoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.AprovacaoIncompleta;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ArquivamentoNaoPermitido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeCuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComentarioObrigatorio;
import br.ufg.inf.labsimulacoes.cenarios.dominio.DecisaoDeCuradoriaNaoPermitida;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ItemDoChecklist;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LimiteDoTesteAtingido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerguntasDeTeste;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TesteNaoPermitido;
import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal;
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
 * não pode aprovar pelo domínio (RN-36).
 *
 * <p>O modo de teste (RF-09) fica no cenário aberto: o curador conversa com o personagem da versão em
 * curadoria, e o contador mostra quantas perguntas valem para o mínimo do RN-06. A pergunta vai pelo HTMX
 * e volta a seção inteira, com a resposta já moderada; uma recusa aparece dentro dela. O checklist
 * completo e o mínimo de perguntas também são exigidos para aprovar (RF-10, RN-06).
 *
 * <p>Abaixo do checklist fica o histórico de versões do cenário aberto (RF-11), com a versão em
 * curadoria comparada à última aprovada, para o curador ver o que mudou desde a que está em sala.
 */
@Controller
@RequestMapping("/curadoria")
class CuradoriaController {

    /** A fila da E1 é curta; com mais que isto, a tela avisa que há outros cenários esperando. */
    private static final int TAMANHO_DA_FILA = 50;

    /**
     * O teste foi recusado: outro curador decidiu o cenário enquanto a tela estava aberta, ou quem pede é o
     * autor, que a tela nem deixa testar (RN-05, RN-36).
     */
    private static final String TESTE_INDISPONIVEL =
            "Este cenário não pode ser testado agora: ele saiu da curadoria ou foi criado por você. Atualize a fila.";

    private final AcervoDaCuradoria acervo;
    private final AprovacaoDeCenario aprovacao;
    private final DevolucaoDeCenario devolucao;
    private final ArquivamentoDeCenario arquivamento;
    private final TesteDoCenario teste;
    private final VersoesDoCenario versoes;
    private final DescricoesDaBncc descricoes;
    private final UsuarioLogado usuarioLogado;

    CuradoriaController(
            AcervoDaCuradoria acervo,
            AprovacaoDeCenario aprovacao,
            DevolucaoDeCenario devolucao,
            ArquivamentoDeCenario arquivamento,
            TesteDoCenario teste,
            VersoesDoCenario versoes,
            DescricoesDaBncc descricoes,
            UsuarioLogado usuarioLogado) {
        this.acervo = acervo;
        this.aprovacao = aprovacao;
        this.devolucao = devolucao;
        this.arquivamento = arquivamento;
        this.teste = teste;
        this.versoes = versoes;
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
        modelo.addAttribute("fila", fila.stream()
                .map(item -> CenariosController.ItemDaLista.naFila(item, trecho.ultimaAprovada(item)))
                .toList());
        modelo.addAttribute("haMais", trecho.temProxima());
        fila.stream()
                .filter(item -> cenario == null || item.id().equals(cenario))
                .findFirst()
                .or(() -> fila.stream().findFirst())
                .ifPresent(aberto -> abrir(aberto, modelo));
        return "curadoria/fila";
    }

    /**
     * O número da aba "Cenários", para a aba de sinalizações (#44, módulo Auditoria) mostrar sem depender
     * deste módulo. Com mais cenários que a fila mostra, vem com "+", como no topo da fila.
     */
    @GetMapping("/contagem")
    String contagem(Model modelo) {
        var trecho = acervo.listar(StatusDoCenario.EM_CURADORIA, 0, TAMANHO_DA_FILA);
        modelo.addAttribute("quantos", trecho.itens().size() + (trecho.temProxima() ? "+" : ""));
        return "curadoria/fila :: contagem";
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

    /**
     * RF-13: tira o cenário da biblioteca sem apagar o histórico (RN-09). O botão fica no detalhe da
     * biblioteca, e a resposta volta à biblioteca, de onde o cenário acabou de sair.
     */
    @PostMapping("/{id}/arquivar")
    String arquivar(@PathVariable long id, RedirectAttributes mensagens) {
        try {
            Cenario arquivado = arquivamento.arquivar(id);
            mensagens.addFlashAttribute("sucesso", "“" + arquivado.versao().conteudo().titulo()
                    + "” foi arquivado e saiu da biblioteca. O histórico continua guardado.");
        } catch (ArquivamentoNaoPermitido erro) {
            mensagens.addFlashAttribute("erro", "Só um cenário aprovado ou suspenso pode ser arquivado.");
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
        }
        return "redirect:/biblioteca";
    }

    // --------------------------------------------------------------------------------------------
    // Modo de teste (RF-09)
    // --------------------------------------------------------------------------------------------

    /**
     * Uma pergunta do curador ao personagem. A resposta chega inteira, já moderada, e volta a seção do
     * modo de teste com a conversa e o contador. Uma recusa volta na mesma seção, com o texto digitado
     * de volta no campo, para o curador não perder o que escreveu.
     */
    @PostMapping("/{id}/teste")
    String perguntarNoTeste(@PathVariable long id, @RequestParam(required = false) String texto, Model modelo) {
        try {
            teste.perguntar(id, texto);
            return secaoDoTeste(id, modelo, null, null);
        } catch (IllegalArgumentException | LimiteDoTesteAtingido erro) {
            return secaoDoTeste(id, modelo, erro.getMessage(), texto);
        } catch (TextoComDadoPessoal erro) {
            return secaoDoTeste(id, modelo, erro.aviso("A pergunta"), null);
        } catch (FalhaNoProvedorDeIa erro) {
            return secaoDoTeste(id, modelo,
                    "O personagem não conseguiu responder agora. Tente de novo em instantes.", texto);
        } catch (TesteNaoPermitido | CenarioNaoEncontrado erro) {
            return foraDoTeste(modelo);
        }
    }

    private String secaoDoTeste(long id, Model modelo, String aviso, String texto) {
        try {
            modelo.addAttribute("rodada", teste.rodada(id));
        } catch (TesteNaoPermitido | CenarioNaoEncontrado erro) {
            return foraDoTeste(modelo);
        }
        modelo.addAttribute("aberto", id);
        modelo.addAttribute("avisoDoTeste", aviso);
        modelo.addAttribute("textoDoTeste", texto);
        modelo.addAttribute("depoisDePerguntar", true);
        return "curadoria/teste :: teste";
    }

    private static String foraDoTeste(Model modelo) {
        modelo.addAttribute("avisoDoTeste", TESTE_INDISPONIVEL);
        return "curadoria/teste :: foraDoTeste";
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    private void abrir(Cenario cenario, Model modelo) {
        long eu = usuarioLogado.id();
        modelo.addAttribute("aberto", cenario.id());
        modelo.addAttribute("conteudo",
                ConteudoExibido.de(cenario.versao().conteudo(), cenario.versao().numero(), descricoes));
        // RN-04: o curador confere os fatos de um cenário sugerido pela IA com as fontes.
        modelo.addAttribute("sugeridoPorIa", cenario.sugeridoPorIa());
        // RN-36: nem o autor do cenário nem quem criou a versão aprova, mesmo com o perfil Curador.
        boolean souAutor = cenario.temAutor(eu) || cenario.versao().criadaPorId() == eu;
        modelo.addAttribute("souAutor", souAutor);
        // RF-10, RN-06: o checklist e quantas perguntas de teste o curador já fez nesta versão.
        modelo.addAttribute("itensDoChecklist", ItemDoChecklist.values());
        modelo.addAttribute("minimoDeTeste", PerguntasDeTeste.MINIMO_PARA_APROVAR);
        modelo.addAttribute("perguntasDeTeste", souAutor ? 0 : aprovacao.perguntasDeTesteFeitas(cenario.id()));
        historicoDeVersoes(cenario, modelo);
        // RN-05: o autor também não testa; a seção aparece, mas sem a conversa.
        if (souAutor) {
            return;
        }
        try {
            modelo.addAttribute("rodada", teste.rodada(cenario.id()));
        } catch (TesteNaoPermitido | CenarioNaoEncontrado erro) {
            // Decidido por outro curador entre a leitura da fila e a da rodada: a seção avisa.
            modelo.addAttribute("avisoDoTeste", TESTE_INDISPONIVEL);
        }
    }

    /**
     * RF-11: as versões do cenário e, se houver mais de uma, a versão em curadoria ao lado da última
     * aprovada. Criar e restaurar versões é do autor, na página de versões, e não aparece na fila.
     */
    private void historicoDeVersoes(Cenario cenario, Model modelo) {
        var historico = HistoricoExibido.de(versoes.historico(cenario.id()), cenario, false);
        modelo.addAttribute("historico", historico);
        if (historico.temComparacao()) {
            modelo.addAttribute("comparacao", ComparacaoExibida.de(
                    versoes.comparar(cenario.id(), historico.antigaPadrao(), historico.novaPadrao())));
        }
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
