package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeTurmas;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeParametros;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaDuplicada;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaNaoEncontrada;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas de administração (RF-02, RF-03, RF-04): usuários e perfis, turmas e parâmetros de uso.
 *
 * <p>Só chama os casos de uso do administrador, que já verificam o perfil (RN-37). Quem não for
 * administrador recebe 403 ao chamar um deles, então as rotas não repetem a verificação. O e-mail e
 * os perfis de quem está logado vêm do {@link AtributosDoUsuario}, para o cabeçalho base.
 */
@Controller
@RequestMapping("/admin")
class AdministracaoController {

    private final GestaoDeUsuarios gestaoDeUsuarios;
    private final CadastroDeTurmas cadastroDeTurmas;
    private final GestaoDeParametros gestaoDeParametros;

    AdministracaoController(
            GestaoDeUsuarios gestaoDeUsuarios,
            CadastroDeTurmas cadastroDeTurmas,
            GestaoDeParametros gestaoDeParametros) {
        this.gestaoDeUsuarios = gestaoDeUsuarios;
        this.cadastroDeTurmas = cadastroDeTurmas;
        this.gestaoDeParametros = gestaoDeParametros;
    }

    // --------------------------------------------------------------------------------------------
    // Usuários (RF-02, RF-03)
    // --------------------------------------------------------------------------------------------

    @GetMapping("/usuarios")
    String usuarios(Model modelo) {
        modelo.addAttribute("usuarios", gestaoDeUsuarios.listar());
        modelo.addAttribute("perfisDisponiveis", Perfil.values());
        return "admin/usuarios";
    }

    @GetMapping("/usuarios/novo")
    String novoUsuario(Model modelo) {
        modelo.addAttribute("perfisDisponiveis", Perfil.values());
        return "admin/usuario-form";
    }

    @PostMapping("/usuarios")
    String cadastrarUsuario(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam String senha,
            @RequestParam(required = false) Set<Perfil> perfis,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "/admin/usuarios/novo", "Usuário adicionado.",
                () -> gestaoDeUsuarios.cadastrar(nome, email, senha, ouVazio(perfis)));
    }

    @PostMapping("/usuarios/{id}/perfis")
    String alterarPerfis(
            @PathVariable long id,
            @RequestParam(required = false) Set<Perfil> perfis,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "/admin/usuarios", "Perfis atualizados.",
                () -> gestaoDeUsuarios.atribuirPerfis(id, ouVazio(perfis)));
    }

    @PostMapping("/usuarios/{id}/ativar")
    String ativarUsuario(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "/admin/usuarios", "Usuário ativado.",
                () -> gestaoDeUsuarios.ativar(id));
    }

    @PostMapping("/usuarios/{id}/desativar")
    String desativarUsuario(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "/admin/usuarios", "Usuário desativado.",
                () -> gestaoDeUsuarios.desativar(id));
    }

    // --------------------------------------------------------------------------------------------
    // Turmas (RF-04, RN-32)
    // --------------------------------------------------------------------------------------------

    @GetMapping("/turmas")
    String turmas(Model modelo) {
        modelo.addAttribute("turmas", cadastroDeTurmas.listar());
        return "admin/turmas";
    }

    @GetMapping("/turmas/nova")
    String novaTurma() {
        return "admin/turma-form";
    }

    @GetMapping("/turmas/{id}/editar")
    String editarTurma(@PathVariable long id, Model modelo, RedirectAttributes mensagens) {
        try {
            modelo.addAttribute("turma", cadastroDeTurmas.buscar(id));
            return "admin/turma-form";
        } catch (TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", erro.getMessage());
            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/turmas")
    String cadastrarTurma(
            @RequestParam String escola,
            @RequestParam String anoSerie,
            @RequestParam String turno,
            @RequestParam String rotulo,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/turmas", "/admin/turmas/nova", "Turma adicionada.",
                () -> cadastroDeTurmas.cadastrar(escola, anoSerie, turno, rotulo));
    }

    @PostMapping("/turmas/{id}")
    String alterarTurma(
            @PathVariable long id,
            @RequestParam String escola,
            @RequestParam String anoSerie,
            @RequestParam String turno,
            @RequestParam String rotulo,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/turmas", "/admin/turmas/" + id + "/editar", "Turma atualizada.",
                () -> cadastroDeTurmas.alterar(id, escola, anoSerie, turno, rotulo));
    }

    @PostMapping("/turmas/{id}/excluir")
    String excluirTurma(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/turmas", "/admin/turmas", "Turma excluída.",
                () -> cadastroDeTurmas.excluir(id));
    }

    // --------------------------------------------------------------------------------------------
    // Parâmetros (RF-04, RN-18, RNF-13)
    // --------------------------------------------------------------------------------------------

    @GetMapping("/parametros")
    String parametros(Model modelo) {
        modelo.addAttribute("parametros", gestaoDeParametros.consultar());
        return "admin/parametros";
    }

    @PostMapping("/parametros")
    String salvarParametros(
            @RequestParam int limiteDePerguntasPorSessao,
            @RequestParam(required = false) String custoDiarioMaximoUsd,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/parametros", "/admin/parametros", "Parâmetros salvos.",
                () -> gestaoDeParametros.alterar(limiteDePerguntasPorSessao, paraValor(custoDiarioMaximoUsd)));
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    /**
     * Executa a ação e redireciona com uma mensagem de sucesso ou de erro. Só trata os erros de
     * negócio e de validação; a falta de perfil (AccessDeniedException) continua virando 403.
     */
    private static String tratar(
            RedirectAttributes mensagens, String aoConcluir, String aoFalhar, String sucesso, Runnable acao) {
        try {
            acao.run();
            mensagens.addFlashAttribute("sucesso", sucesso);
            return "redirect:" + aoConcluir;
        } catch (IllegalArgumentException | IllegalStateException
                | TurmaDuplicada | TurmaEmUso | TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", erro.getMessage());
            return "redirect:" + aoFalhar;
        }
    }

    private static Set<Perfil> ouVazio(Set<Perfil> perfis) {
        return perfis == null ? Set.of() : perfis;
    }

    private static BigDecimal paraValor(String texto) {
        return texto == null || texto.isBlank() ? null : new BigDecimal(texto.trim().replace(',', '.'));
    }
}

