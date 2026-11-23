package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.FiltroDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.PaginaDaBiblioteca;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Biblioteca de cenários aprovados da escola (RF-12), com busca por tema e filtros por componente, ano e
 * habilidade BNCC, como no protótipo. Só aparecem cenários Aprovados, na versão vigente (RN-01); o
 * detalhe mostra o roteiro completo dessa versão, para o professor saber o que vai levar para a sala.
 * No detalhe, o curador também arquiva o cenário (RF-13) e abre o histórico de versões (RF-11).
 *
 * <p>Os filtros vão na URL, por GET: a busca pode ser guardada e compartilhada, e a paginação os mantém.
 *
 * <p>As rotas /biblioteca/** exigem o perfil Professor ou Curador (RN-37), e as consultas conferem de
 * novo.
 */
@Controller
@RequestMapping("/biblioteca")
class BibliotecaController {

    private static final int TAMANHO_DA_PAGINA = 12;

    /** As datas aparecem no horário da escola, e não no do servidor. */
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

    private final BibliotecaDeCenarios biblioteca;
    private final CenariosAprovados aprovados;
    private final DescricoesDaBncc descricoes;

    BibliotecaController(
            BibliotecaDeCenarios biblioteca, CenariosAprovados aprovados, DescricoesDaBncc descricoes) {
        this.biblioteca = biblioteca;
        this.aprovados = aprovados;
        this.descricoes = descricoes;
    }

    /**
     * @param busca tema procurado no título, no componente, no personagem, no contexto ou nos objetivos
     * @param ano vem como texto, porque "Todos" chega vazio; um ano que não é número não filtra
     */
    @GetMapping
    String lista(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String componente,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String habilidade,
            Model modelo) {
        int pedida = Math.max(pagina, 0);
        var filtro = new FiltroDaBiblioteca(busca, componente, numero(ano), habilidade);
        PaginaDaBiblioteca resultado;
        try {
            resultado = biblioteca.listar(filtro, pedida, TAMANHO_DA_PAGINA);
        } catch (IllegalArgumentException invalido) {
            // Só chega aqui por URL montada à mão: a tela limita o texto e oferece só anos válidos.
            modelo.addAttribute("erro", invalido.getMessage() + ".");
            resultado = new PaginaDaBiblioteca(List.of(), pedida, TAMANHO_DA_PAGINA, false);
        }
        modelo.addAttribute("cenarios", resultado.cenarios().stream().map(ItemDaBiblioteca::de).toList());
        modelo.addAttribute("pagina", pedida);
        modelo.addAttribute("temProxima", resultado.temProxima());
        modelo.addAttribute("filtro", filtro);
        modelo.addAttribute("filtrando", filtrando(filtro));
        modelo.addAttribute("opcoes", biblioteca.filtrosDisponiveis());
        modelo.addAttribute("paginaAnterior", endereco(filtro, pedida - 1));
        modelo.addAttribute("paginaProxima", endereco(filtro, pedida + 1));
        return "biblioteca/lista";
    }

    /** Um cenário que não está mais Aprovado (suspenso, arquivado) some da biblioteca e do detalhe. */
    @GetMapping("/{id}")
    String detalhe(@PathVariable long id, Model modelo, RedirectAttributes mensagens) {
        return aprovados.versaoVigente(id)
                .map(versao -> {
                    modelo.addAttribute("conteudo", ConteudoExibido.de(versao, descricoes));
                    modelo.addAttribute("aprovadaEm", data(versao.aprovadaEm()));
                    modelo.addAttribute("cenarioId", id);
                    return "biblioteca/detalhe";
                })
                .orElseGet(() -> {
                    mensagens.addFlashAttribute("erro", "Este cenário não está disponível na biblioteca.");
                    return "redirect:/biblioteca";
                });
    }

    /**
     * A mesma busca em outra página; os filtros vazios ficam fora da URL. Os textos entram como variáveis
     * do template, que o Spring codifica de forma estrita: assim o "+" de "C++" vira %2B, e não um espaço
     * do outro lado.
     */
    private static String endereco(FiltroDaBiblioteca filtro, int pagina) {
        var url = UriComponentsBuilder.fromPath("/biblioteca").queryParam("pagina", Math.max(pagina, 0));
        var valores = new HashMap<String, String>();
        comVariavel(url, valores, "busca", preenchido(filtro.texto()));
        comVariavel(url, valores, "componente", preenchido(filtro.componente()));
        if (filtro.ano() != null) {
            url.queryParam("ano", filtro.ano());
        }
        comVariavel(url, valores, "habilidade", preenchido(filtro.habilidade()));
        return url.encode().buildAndExpand(valores).toUriString();
    }

    private static void comVariavel(
            UriComponentsBuilder url, Map<String, String> valores, String nome, Optional<String> valor) {
        valor.ifPresent(texto -> {
            url.queryParam(nome, "{" + nome + "}");
            valores.put(nome, texto);
        });
    }

    private static Integer numero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(texto.strip());
        } catch (NumberFormatException naoENumero) {
            return null;
        }
    }

    private static boolean filtrando(FiltroDaBiblioteca filtro) {
        return preenchido(filtro.texto()).isPresent() || preenchido(filtro.componente()).isPresent()
                || filtro.ano() != null || preenchido(filtro.habilidade()).isPresent();
    }

    private static Optional<String> preenchido(String texto) {
        return Optional.ofNullable(texto).map(String::strip).filter(valor -> !valor.isEmpty());
    }

    private static String data(Instant momento) {
        return DATA.format(momento);
    }

    /** Um cartão da biblioteca, com a data de aprovação já formatada. */
    record ItemDaBiblioteca(CenarioDaBiblioteca cenario, String aprovadoEm) {

        static ItemDaBiblioteca de(CenarioDaBiblioteca cenario) {
            return new ItemDaBiblioteca(cenario, data(cenario.aprovadoEm()));
        }
    }
}
