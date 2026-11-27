package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioDaEscola;
import br.ufg.inf.labsimulacoes.acesso.UsuariosDaEscola;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.ConsultaDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.CursorDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.FiltroDoLog;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PaginaDoLog;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Consulta do log de ações críticas (RF-26, RN-35), como a aba "Log de auditoria" do protótipo: só leitura,
 * da ação mais recente, com filtros de período, usuário e ação. O administrador a vê entre as abas da
 * administração; o curador, pela fila da curadoria. As rotas de /auditoria são só desses dois perfis
 * (ConfiguracaoDeSeguranca), e a consulta exige o perfil de novo.
 *
 * <p>O período vale em dias inteiros no fuso da escola, com os dois dias incluídos. A página seguinte
 * continua da última linha mostrada (cursor), com os mesmos filtros.
 */
@Controller
@RequestMapping("/auditoria/log")
class LogDeAuditoriaController {

    static final int TAMANHO_DA_PAGINA = 50;

    static final String PERIODO_INVERTIDO = "A data inicial precisa ser igual ou anterior à final.";
    static final String DATA_INVALIDA = "Informe as datas no formato dia/mês/ano.";

    private static final Map<String, AcaoCritica> ACOES = Arrays.stream(AcaoCritica.values())
            .collect(Collectors.toMap(AcaoCritica::name, Function.identity()));

    /** As ações do filtro, com o nome que a tabela usa. */
    private static final List<OpcaoDeAcao> OPCOES_DE_ACAO = Arrays.stream(AcaoCritica.values())
            .map(acao -> new OpcaoDeAcao(acao.name(), LinhaDoLog.rotuloDoFiltro(acao)))
            .toList();

    private final ConsultaDoLog consulta;
    private final UsuariosDaEscola usuarios;
    private final DatasNaTela datas;

    LogDeAuditoriaController(ConsultaDoLog consulta, UsuariosDaEscola usuarios, DatasNaTela datas) {
        this.consulta = consulta;
        this.usuarios = usuarios;
        this.datas = datas;
    }

    /**
     * @param de e {@code ate}: o período, em datas ISO (aaaa-mm-dd), como o campo de data envia
     * @param antesDe e {@code antesDeId}: a última linha da página anterior, para a página seguinte
     */
    @GetMapping
    String log(
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            @RequestParam(required = false) Long usuario,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String antesDe,
            @RequestParam(required = false) Long antesDeId,
            Model modelo) {
        List<UsuarioDaEscola> todos = usuarios.listar();
        Map<Long, String> nomes = todos.stream()
                .collect(Collectors.toMap(UsuarioDaEscola::id, UsuarioDaEscola::nome, (a, b) -> a));
        AcaoCritica acaoEscolhida = acaoDe(acao);

        modelo.addAttribute("usuarios", todos);
        modelo.addAttribute("acoes", OPCOES_DE_ACAO);
        modelo.addAttribute("de", vazioSeNulo(de));
        modelo.addAttribute("ate", vazioSeNulo(ate));
        modelo.addAttribute("usuarioEscolhido", usuario);
        modelo.addAttribute("acaoEscolhida", acaoEscolhida == null ? "" : acaoEscolhida.name());
        modelo.addAttribute("filtrado", de != null && !de.isBlank() || ate != null && !ate.isBlank()
                || usuario != null || acaoEscolhida != null);
        modelo.addAttribute("continuacao", antesDe != null);

        FiltroDoLog filtro;
        try {
            Instant inicio = inicioDe(de);
            Instant fim = fimDe(ate);
            if (inicio != null && fim != null && !inicio.isBefore(fim)) {
                return semResultado(modelo, PERIODO_INVERTIDO);
            }
            filtro = new FiltroDoLog(inicio, fim, usuario != null && usuario > 0 ? usuario : null,
                    acaoEscolhida, cursor(antesDe, antesDeId), TAMANHO_DA_PAGINA);
        } catch (DateTimeException erro) {
            // Inclui a data que não se lê e a que se lê, mas passa do maior dia possível ao somar um dia.
            return semResultado(modelo, DATA_INVALIDA);
        }

        PaginaDoLog pagina = consulta.consultar(filtro);
        modelo.addAttribute("linhas", pagina.linhas().stream()
                .map(linha -> LinhaDoLog.de(linha, nomes, datas))
                .toList());
        pagina.cursorDaProxima().ifPresent(proxima -> {
            modelo.addAttribute("proximaAntesDe", proxima.ocorridoEm().toString());
            modelo.addAttribute("proximaAntesDeId", proxima.id());
        });
        return "auditoria/log";
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    record OpcaoDeAcao(String valor, String rotulo) {}

    private static String semResultado(Model modelo, String erro) {
        modelo.addAttribute("erro", erro);
        modelo.addAttribute("linhas", List.of());
        return "auditoria/log";
    }

    /** O começo do dia inicial. */
    private Instant inicioDe(String dia) {
        return dia == null || dia.isBlank() ? null : datas.inicioDo(LocalDate.parse(dia));
    }

    /** O começo do dia seguinte ao final, que fica de fora: assim o dia final entra inteiro. */
    private Instant fimDe(String dia) {
        return dia == null || dia.isBlank() ? null : datas.inicioDo(LocalDate.parse(dia).plusDays(1));
    }

    /** Um cursor inválido na URL volta para a primeira página, em vez de dar erro. */
    private static CursorDoLog cursor(String antesDe, Long antesDeId) {
        if (antesDe == null || antesDeId == null) {
            return null;
        }
        try {
            return new CursorDoLog(Instant.parse(antesDe), antesDeId);
        } catch (DateTimeParseException | IllegalArgumentException erro) {
            return null;
        }
    }

    /** Um valor fora da lista vale como "todas as ações". */
    private static AcaoCritica acaoDe(String valor) {
        return valor == null ? null : ACOES.get(valor);
    }

    private static String vazioSeNulo(String valor) {
        return valor == null ? "" : valor;
    }
}
