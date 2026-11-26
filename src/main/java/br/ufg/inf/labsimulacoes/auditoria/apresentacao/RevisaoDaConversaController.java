package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.auditoria.aplicacao.RegistroDeSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.RevisaoDaConversa;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.RevisaoDaConversa.Revisao;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.RevisaoDaConversa.Situacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.ConversaNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.ForaDoPrazoDeSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RespostaNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoRepetida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Revisão da conversa depois da aula (RF-23, RN-21): as sessões recentes do professor e a conversa de uma
 * delas, só para leitura, com o botão de sinalizar em cada resposta ainda no prazo. A sinalização durante
 * a aula, no modo projeção, é da #43.
 *
 * <p>A sessão de outro professor responde 404, como uma que não existe (RN-37). A sinalização volta como
 * o fragmento do formulário, com status 200 também na recusa, como na curadoria: o aviso aparece no
 * lugar do formulário, sem depender do tratamento de erros do HTMX.
 */
@Controller
@RequestMapping("/sessoes")
class RevisaoDaConversaController {

    static final String SUCESSO = "Sinalização enviada. A curadoria vai analisar a resposta.";

    private final RevisaoDaConversa revisoes;
    private final RegistroDeSinalizacao registro;

    RevisaoDaConversaController(RevisaoDaConversa revisoes, RegistroDeSinalizacao registro) {
        this.revisoes = revisoes;
        this.registro = registro;
    }

    @ModelAttribute("tipos")
    List<TipoDeErro> tipos() {
        return Arrays.asList(TipoDeErro.values());
    }

    @ModelAttribute("gravidades")
    List<GravidadePercebida> gravidades() {
        return Arrays.asList(GravidadePercebida.values());
    }

    @GetMapping("/revisao")
    String recentes(Model modelo) {
        modelo.addAttribute("sessoes", revisoes.sessoesRecentes().stream()
                .map(recente -> SessaoNaTela.de(recente.sessao(), recente.limite()))
                .toList());
        return "sinalizacoes/recentes";
    }

    @GetMapping("/{id}/revisao")
    String revisao(@PathVariable long id, Model modelo, HttpServletResponse resposta) {
        Revisao revisao;
        try {
            revisao = revisoes.revisao(id);
        } catch (ConversaNaoEncontrada erro) {
            resposta.setStatus(HttpStatus.NOT_FOUND.value());
            modelo.addAttribute("erro", "Sessão não encontrada. Escolha uma das suas sessões recentes.");
            return recentes(modelo);
        }
        modelo.addAttribute("sessao", SessaoNaTela.de(revisao.sessao(), revisao.limite()));
        modelo.addAttribute("itens", revisao.itens());
        modelo.addAttribute("podeSinalizar", revisao.podeSinalizar());
        return "sinalizacoes/revisao";
    }

    @PostMapping("/{id}/revisao/respostas/{mensagemId}/sinalizacao")
    String sinalizar(
            @PathVariable long id,
            @PathVariable long mensagemId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String gravidade,
            @RequestParam(required = false) String justificativa,
            Model modelo) {
        modelo.addAttribute("sessaoId", id);
        modelo.addAttribute("mensagemId", mensagemId);
        modelo.addAttribute("tipoEscolhido", tipo);
        modelo.addAttribute("gravidadeEscolhida", gravidade);
        modelo.addAttribute("justificativa", justificativa);
        try {
            registro.sinalizar(id, mensagemId, enumDe(TipoDeErro.class, tipo),
                    enumDe(GravidadePercebida.class, gravidade), justificativa);
            return fragmento(modelo, Situacao.SINALIZADA, null, SUCESSO);
        } catch (IllegalArgumentException erro) {
            return fragmento(modelo, Situacao.PODE_SINALIZAR, erro.getMessage(), null);
        } catch (TextoComDadoPessoal erro) {
            // RN-13, RNF-01: a justificativa não volta à tela, para o dado não ser repetido.
            modelo.addAttribute("justificativa", null);
            return fragmento(modelo, Situacao.PODE_SINALIZAR, erro.aviso("A justificativa"), null);
        } catch (SinalizacaoRepetida erro) {
            return fragmento(modelo, Situacao.SINALIZADA, "Você já tinha sinalizado esta resposta.", null);
        } catch (ForaDoPrazoDeSinalizacao erro) {
            return fragmento(modelo, Situacao.FORA_DO_PRAZO,
                    "O prazo para sinalizar terminou em " + SessaoNaTela.momento(erro.limite()) + ".", null);
        } catch (RespostaNaoEncontrada erro) {
            return fragmento(modelo, Situacao.FORA_DO_PRAZO, "Resposta não encontrada.", null);
        }
    }

    private static String fragmento(Model modelo, Situacao situacao, String aviso, String sucesso) {
        modelo.addAttribute("situacao", situacao.name());
        modelo.addAttribute("aviso", aviso);
        modelo.addAttribute("sucesso", sucesso);
        return "sinalizacoes/formulario :: sinalizar";
    }

    /** Um valor que não está na lista vira nulo, e o domínio pede a escolha com a mensagem dele. */
    private static <T extends Enum<T>> T enumDe(Class<T> tipo, String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
