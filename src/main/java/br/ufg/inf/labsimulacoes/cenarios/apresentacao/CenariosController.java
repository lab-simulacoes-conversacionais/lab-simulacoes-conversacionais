package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.CatalogoBncc;
import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.AutoriaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.aplicacao.EnvioACuradoria;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioForaDeRascunho;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioIncompleto;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadesForaDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PendenciaDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Criação e edição de cenário pelo autor (RF-06), com busca de habilidades da BNCC (RF-05) e envio à
 * curadoria (RF-08). Também a lista dos cenários do autor, por onde ele volta ao rascunho e lê a
 * devolução do curador (RN-07, RN-37).
 *
 * <p>As rotas /cenarios/** exigem o perfil Professor ou Curador na configuração de segurança, e os
 * casos de uso conferem de novo. Só o autor edita o próprio rascunho.
 *
 * <p>Salvar grava o rascunho, que pode estar incompleto. Enviar grava e, em seguida, envia à curadoria:
 * se faltar algo, o rascunho fica salvo e a tela mostra o que falta. A lista "Pronto para curadoria?"
 * é atualizada pelo HTMX enquanto o autor digita, sem gravar nada.
 */
@Controller
@RequestMapping("/cenarios")
class CenariosController {

    private static final String EDITOR = "cenarios/editor";
    private static final String ENVIAR = "enviar";
    private static final int TAMANHO_DA_LISTA = 12;
    private static final int LIMITE_DA_BUSCA = 10;

    /** Código oficial completo, como EF08HI03; o resto da busca procura na descrição. */
    private static final Pattern CODIGO_DA_BNCC = Pattern.compile("[A-Za-z]{2}\\d{2}[A-Za-z]{2,4}\\d{2,3}");

    /** Componentes do Ensino Fundamental, com o nome usado no catálogo (bncc/estrutura.json). */
    static final List<String> COMPONENTES = List.of(
            "Arte", "Ciências", "Educação Física", "Ensino Religioso", "Geografia", "História",
            "Língua Inglesa", "Língua Portuguesa", "Matemática");

    static final List<Integer> ANOS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);

    private final AutoriaDeCenarios autoria;
    private final EnvioACuradoria envio;
    private final CatalogoBncc catalogo;
    private final DescricoesDaBncc descricoes;
    private final UsuarioLogado usuarioLogado;

    CenariosController(
            AutoriaDeCenarios autoria,
            EnvioACuradoria envio,
            CatalogoBncc catalogo,
            DescricoesDaBncc descricoes,
            UsuarioLogado usuarioLogado) {
        this.autoria = autoria;
        this.envio = envio;
        this.catalogo = catalogo;
        this.descricoes = descricoes;
        this.usuarioLogado = usuarioLogado;
    }

    // --------------------------------------------------------------------------------------------
    // Meus cenários (RN-37, RN-07)
    // --------------------------------------------------------------------------------------------

    @GetMapping
    String meusCenarios(@RequestParam(defaultValue = "0") int pagina, Model modelo) {
        var trecho = autoria.meusCenarios(Math.max(pagina, 0), TAMANHO_DA_LISTA);
        modelo.addAttribute("cenarios", trecho.itens().stream().map(ItemDaLista::de).toList());
        modelo.addAttribute("pagina", Math.max(pagina, 0));
        modelo.addAttribute("temProxima", trecho.temProxima());
        return "cenarios/lista";
    }

    // --------------------------------------------------------------------------------------------
    // Editor (RF-06)
    // --------------------------------------------------------------------------------------------

    @GetMapping("/novo")
    String novo(Model modelo) {
        return editor(modelo, null, FormularioDoCenario.vazio(), null);
    }

    @PostMapping
    String criar(
            @ModelAttribute FormularioDoCenario formulario,
            @RequestParam(defaultValue = "salvar") String acao,
            Model modelo,
            RedirectAttributes mensagens) {
        Cenario criado;
        try {
            criado = autoria.criar(formulario.paraConteudo());
        } catch (IllegalArgumentException | HabilidadesForaDoCatalogo erro) {
            return editor(modelo, null, formulario, erro.getMessage());
        }
        return depoisDeSalvar(criado, acao, mensagens);
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable long id, Model modelo, RedirectAttributes mensagens) {
        Cenario cenario;
        try {
            cenario = doAutor(id);
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
            return "redirect:/cenarios";
        }
        if (cenario.versao().status() != StatusDaVersao.RASCUNHO) {
            mensagens.addFlashAttribute("erro", "“" + cenario.versao().conteudo().titulo()
                    + "” está " + ItemDaLista.rotulo(cenario.status()).toLowerCase(Locale.ROOT)
                    + " e não pode ser editado agora.");
            return "redirect:/cenarios";
        }
        modelo.addAttribute("devolucao", cenario.ultimaDevolucao());
        return editor(modelo, id, FormularioDoCenario.de(cenario.versao().conteudo()), null);
    }

    @PostMapping("/{id}")
    String alterar(
            @PathVariable long id,
            @ModelAttribute FormularioDoCenario formulario,
            @RequestParam(defaultValue = "salvar") String acao,
            Model modelo,
            RedirectAttributes mensagens) {
        Cenario alterado;
        try {
            alterado = autoria.alterarRascunho(id, formulario.paraConteudo());
        } catch (IllegalArgumentException | HabilidadesForaDoCatalogo erro) {
            return editor(modelo, id, formulario, erro.getMessage());
        } catch (CenarioForaDeRascunho erro) {
            mensagens.addFlashAttribute("erro", "Este cenário já saiu do rascunho e não pode mais ser editado.");
            return "redirect:/cenarios";
        } catch (CenarioNaoEncontrado erro) {
            mensagens.addFlashAttribute("erro", "Este cenário não existe.");
            return "redirect:/cenarios";
        }
        return depoisDeSalvar(alterado, acao, mensagens);
    }

    /** Só existe logo depois de um envio; aberta direto, volta à lista. */
    @GetMapping("/enviado")
    String enviado(Model modelo) {
        return modelo.containsAttribute("enviado") ? "cenarios/enviado" : "redirect:/cenarios";
    }

    // --------------------------------------------------------------------------------------------
    // Fragmentos do HTMX
    // --------------------------------------------------------------------------------------------

    /**
     * "Pronto para curadoria?" com o que está no formulário, sem gravar. O HTMX chama a cada alteração,
     * para o autor ver na hora o que falta (RN-02, RN-03).
     */
    @PostMapping("/pendencias")
    String pendencias(@ModelAttribute FormularioDoCenario formulario, Model modelo) {
        modelo.addAttribute("pendencias", formulario.pendencias());
        return "cenarios/fragmentos :: requisitos";
    }

    /**
     * Busca de habilidades da BNCC (RF-05). Um código completo procura o código; o resto procura na
     * descrição. Componente e ano escolhidos no editor restringem o resultado.
     */
    @GetMapping("/bncc")
    String buscarNaBncc(
            @RequestParam(name = "bncc-busca", required = false) String busca,
            @RequestParam(required = false) String componente,
            @RequestParam(required = false) Integer ano,
            Model modelo) {
        String termo = busca == null ? "" : busca.strip();
        boolean semFiltro = termo.isEmpty() && vazio(componente) && ano == null;
        List<HabilidadeDoCatalogo> encontradas = semFiltro ? List.of() : catalogo.buscar(consulta(termo, componente, ano));
        modelo.addAttribute("buscou", !semFiltro);
        modelo.addAttribute("encontradas", encontradas);
        return "cenarios/fragmentos :: resultadosDaBncc";
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    private Cenario doAutor(long id) {
        Cenario cenario = autoria.buscar(id);
        if (!cenario.temAutor(usuarioLogado.id())) {
            // O curador pode ver o cenário de outro autor, mas não editá-lo (Figura 3 dos requisitos).
            throw new AccessDeniedException("Só o autor edita o rascunho do cenário");
        }
        return cenario;
    }

    private String depoisDeSalvar(Cenario cenario, String acao, RedirectAttributes mensagens) {
        if (!ENVIAR.equals(acao)) {
            mensagens.addFlashAttribute("sucesso", "Rascunho salvo.");
            return "redirect:/cenarios/" + cenario.id() + "/editar";
        }
        try {
            Cenario enviado = envio.enviar(cenario.id());
            mensagens.addFlashAttribute("enviado", ItemDaLista.de(enviado));
            return "redirect:/cenarios/enviado";
        } catch (CenarioIncompleto incompleto) {
            mensagens.addFlashAttribute("erro", "O rascunho foi salvo, mas ainda não pode ir à curadoria. Falta: "
                    + incompleto.pendencias().stream()
                            .map(PendenciaDoCenario::descricao)
                            .collect(Collectors.joining(", "))
                    + ".");
            return "redirect:/cenarios/" + cenario.id() + "/editar";
        }
    }

    private String editor(Model modelo, Long id, FormularioDoCenario formulario, String erro) {
        modelo.addAttribute("id", id);
        modelo.addAttribute("formulario", formulario);
        modelo.addAttribute("habilidadesEscolhidas", descricoes.de(formulario.habilidades()));
        modelo.addAttribute("pendencias", formulario.pendencias());
        modelo.addAttribute("componentes", COMPONENTES);
        modelo.addAttribute("anos", ANOS);
        modelo.addAttribute("duracaoMinima", ConteudoDoCenario.DURACAO_MINIMA_MIN);
        modelo.addAttribute("duracaoMaxima", ConteudoDoCenario.DURACAO_MAXIMA_MIN);
        if (erro != null) {
            modelo.addAttribute("erro", erro);
        }
        return EDITOR;
    }

    private static ConsultaAoCatalogo consulta(String termo, String componente, Integer ano) {
        var consulta = ConsultaAoCatalogo.todas()
                .comEtapa(FormularioDoCenario.ETAPA)
                .comAno(ano)
                .comComponente(vazio(componente) ? null : componente)
                .comLimite(LIMITE_DA_BUSCA);
        if (termo.isEmpty()) {
            return consulta;
        }
        return CODIGO_DA_BNCC.matcher(termo).matches() ? consulta.comCodigo(termo) : consulta.comDescricao(termo);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Um cenário na lista do autor e na confirmação do envio. */
    record ItemDaLista(
            long id,
            String titulo,
            String personagem,
            String componente,
            Integer ano,
            List<String> habilidades,
            StatusDoCenario status,
            String devolucao) {

        static ItemDaLista de(Cenario cenario) {
            var conteudo = cenario.versao().conteudo();
            boolean devolvido = cenario.status() == StatusDoCenario.RASCUNHO && cenario.ultimaDevolucao() != null;
            return new ItemDaLista(
                    cenario.id(),
                    conteudo.titulo(),
                    conteudo.roteiro().personagem(),
                    conteudo.curriculo().componente(),
                    conteudo.curriculo().ano(),
                    conteudo.curriculo().habilidades(),
                    cenario.status(),
                    devolvido ? cenario.ultimaDevolucao().comentario() : null);
        }

        public String rotuloDoStatus() {
            return devolucao != null ? "Devolvido" : rotulo(status);
        }

        /** Classe do selo, nas cores do protótipo. */
        public String classeDoStatus() {
            if (devolucao != null) {
                return "badge-danger";
            }
            return switch (status) {
                case RASCUNHO, EM_CURADORIA -> "badge-pending";
                case APROVADO -> "badge-positive";
                case SUSPENSO -> "badge-danger";
                case ARQUIVADO -> "badge-neutral";
            };
        }

        /** Ícone do selo, como no protótipo. */
        public String iconeDoStatus() {
            if (devolucao != null) {
                return "!";
            }
            return switch (status) {
                case RASCUNHO -> "•";
                case EM_CURADORIA -> "◷";
                case APROVADO -> "✓";
                case SUSPENSO -> "!";
                case ARQUIVADO -> "×";
            };
        }

        public boolean editavel() {
            return status == StatusDoCenario.RASCUNHO;
        }

        static String rotulo(StatusDoCenario status) {
            return switch (status) {
                case RASCUNHO -> "Rascunho";
                case EM_CURADORIA -> "Em curadoria";
                case APROVADO -> "Aprovado";
                case SUSPENSO -> "Suspenso";
                case ARQUIVADO -> "Arquivado";
            };
        }
    }
}
