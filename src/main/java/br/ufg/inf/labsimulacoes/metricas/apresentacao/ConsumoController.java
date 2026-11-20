package br.ufg.inf.labsimulacoes.metricas.apresentacao;

import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Aba Consumo da administração (RNF-13): o custo da IA hoje, o limite, as respostas sem custo e a busca
 * do custo de uma sessão pelo id. O caminho fica em /admin, que só o administrador abre.
 */
@Controller
@RequestMapping("/admin/consumo")
class ConsumoController {

    private final ConsumoDaIaNaAdministracao consumo;

    ConsumoController(ConsumoDaIaNaAdministracao consumo) {
        this.consumo = consumo;
    }

    /** O aviso do topo vem do mesmo consumo do dia, somado uma vez só; o AvisoDeCustoNaAdministracao pula esta tela. */
    @GetMapping
    String consumo(@RequestParam(required = false) Long sessao, Model modelo) {
        ConsumoDaIaNaAdministracao.ConsumoDoDia hoje = consumo.hoje();
        modelo.addAttribute("hoje", hoje);
        hoje.aviso().ifPresent(aviso -> modelo.addAttribute(AvisoDeCustoNaAdministracao.ATRIBUTO, aviso));
        if (sessao != null) {
            modelo.addAttribute("sessao", sessao);
            modelo.addAttribute("consumoDaSessao", consumo.daSessao(sessao));
        }
        return "admin/consumo";
    }
}
