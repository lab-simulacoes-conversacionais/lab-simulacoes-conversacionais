package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.ItemDaFila;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.PaginaDeItens;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.ResumoDoPrazo;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.TriagemDeSinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CursorDaFila;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadeFinal;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PerfilDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoJaTriada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoPendente;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A aba "Sinalizações" da fila do curador (RF-24), como no protótipo: as pendentes da mais antiga, com o
 * prazo de 2 dias úteis e as atrasadas em destaque (RN-24). Confirmar e descartar abrem um modal com a
 * pergunta, a resposta e a justificativa, para o curador decidir lendo o trecho; na confirmação ele escolhe
 * a gravidade final (RN-22), que começa na percebida por quem sinalizou.
 *
 * <p>A triagem volta para a fila com a confirmação ou a recusa (POST, redirecionamento e GET), como a
 * aprovação de cenários. As rotas de /curadoria são só do curador (ConfiguracaoDeSeguranca), e os casos de
 * uso exigem o perfil de novo.
 */
@Controller
@RequestMapping("/curadoria/sinalizacoes")
class TriagemController {

    static final int TAMANHO_DA_PAGINA = 20;

    /** Dois curadores na mesma fila: a segunda triagem não encontra mais a pendente. */
    static final String JA_TRIADA = "Esta sinalização já foi triada ou não existe mais. A fila foi atualizada.";

    static final String SEM_GRAVIDADE = "Escolha a gravidade final para confirmar a sinalização.";

    private static final String VOLTAR = "redirect:/curadoria/sinalizacoes";

    private final FilaDeTriagem fila;
    private final TriagemDeSinalizacoes triagem;
    private final CenariosAprovados cenarios;

    TriagemController(FilaDeTriagem fila, TriagemDeSinalizacoes triagem, CenariosAprovados cenarios) {
        this.fila = fila;
        this.triagem = triagem;
        this.cenarios = cenarios;
    }

    /**
     * @param depoisDe e {@code depoisDeId}: a última sinalização da página anterior, para a página seguinte
     */
    @GetMapping
    String fila(
            @RequestParam(required = false) String depoisDe,
            @RequestParam(required = false) Long depoisDeId,
            Model modelo) {
        PaginaDeItens pagina = fila.pendentes(cursor(depoisDe, depoisDeId), TAMANHO_DA_PAGINA);
        var titulos = new HashMap<Long, String>();
        modelo.addAttribute("sinalizacoes", pagina.itens().stream()
                .map(item -> SinalizacaoNaFila.de(item, titulo(item.sinalizacao().versaoId(), titulos)))
                .toList());
        ResumoDoPrazo resumo = fila.resumoDoPrazo();
        modelo.addAttribute("pendentes", resumo.pendentes());
        modelo.addAttribute("atrasadas", resumo.foraDoPrazo().size());
        modelo.addAttribute("continuacao", depoisDe != null);
        if (pagina.temProxima()) {
            modelo.addAttribute("proximaDepoisDe", pagina.proxima().criadaEm().toString());
            modelo.addAttribute("proximaDepoisDeId", pagina.proxima().id());
        }
        modelo.addAttribute("gravidades", Arrays.asList(GravidadeFinal.values()));
        return "sinalizacoes/fila";
    }

    /** O número da aba, para a fila de cenários mostrar sem depender deste módulo (curadoria/fila.html). */
    @GetMapping("/contagem")
    String contagem(Model modelo) {
        modelo.addAttribute("contagem", fila.resumoDoPrazo().pendentes());
        return "sinalizacoes/fila :: contagem";
    }

    @PostMapping("/{id}/confirmar")
    String confirmar(@PathVariable long id, @RequestParam(required = false) String gravidade,
            RedirectAttributes mensagens) {
        GravidadeFinal escolhida = gravidadeDe(gravidade);
        if (escolhida == null) {
            mensagens.addFlashAttribute("erro", SEM_GRAVIDADE);
            return VOLTAR;
        }
        try {
            triagem.confirmar(id, escolhida);
            mensagens.addFlashAttribute("sucesso", "Sinalização confirmada como " + escolhida.rotulo() + ".");
        } catch (SinalizacaoNaoEncontrada | SinalizacaoJaTriada erro) {
            mensagens.addFlashAttribute("erro", JA_TRIADA);
        }
        return VOLTAR;
    }

    @PostMapping("/{id}/descartar")
    String descartar(@PathVariable long id, RedirectAttributes mensagens) {
        try {
            triagem.descartar(id);
            mensagens.addFlashAttribute("sucesso", "Sinalização descartada.");
        } catch (SinalizacaoNaoEncontrada | SinalizacaoJaTriada erro) {
            mensagens.addFlashAttribute("erro", JA_TRIADA);
        }
        return VOLTAR;
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    /** Um cursor inválido na URL volta para a primeira página, em vez de dar erro. */
    private static CursorDaFila cursor(String depoisDe, Long depoisDeId) {
        if (depoisDe == null || depoisDeId == null) {
            return null;
        }
        try {
            return new CursorDaFila(Instant.parse(depoisDe), depoisDeId);
        } catch (DateTimeParseException | IllegalArgumentException erro) {
            return null;
        }
    }

    /** Nulo sem a gravidade ou com um valor fora da lista. */
    private static GravidadeFinal gravidadeDe(String valor) {
        if (valor == null) {
            return null;
        }
        return Arrays.stream(GravidadeFinal.values()).filter(g -> g.name().equals(valor)).findFirst().orElse(null);
    }

    /** As sinalizações da mesma versão leem o título uma vez só. */
    private String titulo(long versaoId, Map<Long, String> lidos) {
        return lidos.computeIfAbsent(versaoId, id -> cenarios.versaoAprovada(id)
                .map(VersaoAprovada::titulo)
                .orElse("Cenário sem versão aprovada"));
    }

    /**
     * Uma linha da fila, já como a tela mostra.
     *
     * @param gravidade a percebida por quem sinalizou
     * @param classeDaGravidade o sufixo da classe do selo: leve, moderada ou grave
     * @param prazo até quando triar, como "18 jun · 15:00"
     * @param atrasada se passou do prazo e fica em destaque (RN-24)
     */
    record SinalizacaoNaFila(
            long id,
            String cenario,
            String tipo,
            String gravidade,
            String gravidadePercebida,
            String classeDaGravidade,
            String abertura,
            String quemSinalizou,
            String prazo,
            boolean atrasada,
            String pergunta,
            String resposta,
            String justificativa) {

        static SinalizacaoNaFila de(ItemDaFila item, String cenario) {
            SinalizacaoPendente sinalizacao = item.sinalizacao();
            return new SinalizacaoNaFila(
                    sinalizacao.id(),
                    cenario,
                    sinalizacao.tipo().rotulo(),
                    sinalizacao.gravidadePercebida().rotulo(),
                    sinalizacao.gravidadePercebida().name(),
                    sinalizacao.gravidadePercebida().name().toLowerCase(Locale.ROOT),
                    DatasNaTela.diaEHora(sinalizacao.criadaEm()),
                    sinalizacao.perfil() == PerfilDaSinalizacao.PROFESSOR ? "pelo professor" : "pelo curador",
                    DatasNaTela.limite(item.limite()),
                    item.foraDoPrazo(),
                    sinalizacao.pergunta(),
                    sinalizacao.resposta(),
                    sinalizacao.justificativa());
        }
    }
}
