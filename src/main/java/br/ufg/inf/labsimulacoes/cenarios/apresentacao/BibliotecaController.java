package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Biblioteca de cenários aprovados da escola (RF-12), em lista simples na E1: busca e filtros chegam
 * com a #34. Só aparecem cenários Aprovados, na versão vigente (RN-01); o detalhe mostra o roteiro
 * completo dessa versão, para o professor saber o que vai levar para a sala.
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

    @GetMapping
    String lista(@RequestParam(defaultValue = "0") int pagina, Model modelo) {
        int pedida = Math.max(pagina, 0);
        var resultado = biblioteca.listar(pedida, TAMANHO_DA_PAGINA);
        modelo.addAttribute("cenarios", resultado.cenarios().stream().map(ItemDaBiblioteca::de).toList());
        modelo.addAttribute("pagina", pedida);
        modelo.addAttribute("temProxima", resultado.temProxima());
        return "biblioteca/lista";
    }

    /** Um cenário que não está mais Aprovado (suspenso, arquivado) some da biblioteca e do detalhe. */
    @GetMapping("/{id}")
    String detalhe(@PathVariable long id, Model modelo, RedirectAttributes mensagens) {
        return aprovados.versaoVigente(id)
                .map(versao -> {
                    modelo.addAttribute("conteudo", ConteudoExibido.de(versao, descricoes));
                    modelo.addAttribute("aprovadaEm", data(versao.aprovadaEm()));
                    return "biblioteca/detalhe";
                })
                .orElseGet(() -> {
                    mensagens.addFlashAttribute("erro", "Este cenário não está disponível na biblioteca.");
                    return "redirect:/biblioteca";
                });
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
