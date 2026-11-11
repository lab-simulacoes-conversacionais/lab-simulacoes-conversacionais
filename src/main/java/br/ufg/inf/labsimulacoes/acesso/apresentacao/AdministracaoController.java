package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeTurmas;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeParametros;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.GestaoDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaDuplicada;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaEmUso;
import br.ufg.inf.labsimulacoes.acesso.dominio.TurmaNaoEncontrada;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
 * <p>As rotas /admin/** exigem o perfil Administrador na configuração de segurança, e os casos de uso
 * conferem de novo (RN-37). O e-mail e os perfis de quem está logado vêm do {@link AtributosDoUsuario}.
 *
 * <p>Um erro de validação devolve o próprio formulário com o que foi digitado e a mensagem; um
 * cadastro concluído leva à tela de confirmação, como no protótipo.
 */
@Controller
@RequestMapping("/admin")
class AdministracaoController {

    private static final String FORMULARIO_DE_USUARIO = "admin/usuario-form";
    private static final String FORMULARIO_DE_TURMA = "admin/turma-form";

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
        modelo.addAttribute("opcoesDePerfil", NomesDosPerfis.opcoes());
        return "admin/usuarios";
    }

    @GetMapping("/usuarios/novo")
    String novoUsuario(Model modelo) {
        return formularioDeUsuario(modelo, "", "", Set.of(Perfil.PROFESSOR), null);
    }

    @PostMapping("/usuarios")
    String cadastrarUsuario(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam String senha,
            @RequestParam(required = false) Set<Perfil> perfis,
            Model modelo,
            RedirectAttributes mensagens) {
        try {
            Usuario novo = gestaoDeUsuarios.cadastrar(nome, email, senha, ouVazio(perfis));
            mensagens.addFlashAttribute("concluido", new Conclusao(
                    "USUÁRIO ADICIONADO",
                    "Acesso preparado",
                    "O profissional já pode entrar com o e-mail e a senha provisória.",
                    "Ativo",
                    novo.nome(),
                    rotulos(novo.perfis()) + " · " + novo.email(),
                    "/admin/usuarios",
                    "Voltar para usuários"));
            return "redirect:/admin/concluido";
        } catch (IllegalArgumentException erro) {
            return formularioDeUsuario(modelo, nome, email, ouVazio(perfis), erro.getMessage());
        }
    }

    @PostMapping("/usuarios/{id}/perfis")
    String alterarPerfis(
            @PathVariable long id,
            @RequestParam(required = false) Set<Perfil> perfis,
            RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "Perfis atualizados.",
                () -> gestaoDeUsuarios.atribuirPerfis(id, ouVazio(perfis)));
    }

    @PostMapping("/usuarios/{id}/ativar")
    String ativarUsuario(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "Usuário ativado.", () -> gestaoDeUsuarios.ativar(id));
    }

    @PostMapping("/usuarios/{id}/desativar")
    String desativarUsuario(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/usuarios", "Usuário desativado.", () -> gestaoDeUsuarios.desativar(id));
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
    String novaTurma(Model modelo) {
        return formularioDeTurma(modelo, new DadosDaTurma(null, "", "", "", ""), null);
    }

    @GetMapping("/turmas/{id}/editar")
    String editarTurma(@PathVariable long id, Model modelo, RedirectAttributes mensagens) {
        try {
            return formularioDeTurma(modelo, DadosDaTurma.de(cadastroDeTurmas.buscar(id)), null);
        } catch (TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", erro.getMessage());
            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/turmas")
    String cadastrarTurma(
            @RequestParam String escola,
            @RequestParam String anoSerie,
            @RequestParam(required = false) String turno,
            @RequestParam String rotulo,
            Model modelo,
            RedirectAttributes mensagens) {
        var dados = new DadosDaTurma(null, escola, anoSerie, turno, rotulo);
        try {
            Turma turma = cadastroDeTurmas.cadastrar(escola, anoSerie, turno, rotulo);
            mensagens.addFlashAttribute("concluido", turmaSalva(turma, "Turma adicionada"));
            return "redirect:/admin/concluido";
        } catch (IllegalArgumentException | TurmaDuplicada erro) {
            return formularioDeTurma(modelo, dados, erro.getMessage());
        }
    }

    @PostMapping("/turmas/{id}")
    String alterarTurma(
            @PathVariable long id,
            @RequestParam String escola,
            @RequestParam String anoSerie,
            @RequestParam(required = false) String turno,
            @RequestParam String rotulo,
            Model modelo,
            RedirectAttributes mensagens) {
        var dados = new DadosDaTurma(id, escola, anoSerie, turno, rotulo);
        try {
            Turma turma = cadastroDeTurmas.alterar(id, escola, anoSerie, turno, rotulo);
            mensagens.addFlashAttribute("concluido", turmaSalva(turma, "Cadastro atualizado"));
            return "redirect:/admin/concluido";
        } catch (IllegalArgumentException | TurmaDuplicada erro) {
            return formularioDeTurma(modelo, dados, erro.getMessage());
        } catch (TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", erro.getMessage());
            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/turmas/{id}/excluir")
    String excluirTurma(@PathVariable long id, RedirectAttributes mensagens) {
        return tratar(mensagens, "/admin/turmas", "Turma excluída.", () -> cadastroDeTurmas.excluir(id));
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
        return tratar(mensagens, "/admin/parametros", "Parâmetros salvos",
                () -> gestaoDeParametros.alterar(limiteDePerguntasPorSessao, paraValor(custoDiarioMaximoUsd)));
    }

    // --------------------------------------------------------------------------------------------
    // Confirmação de cadastro
    // --------------------------------------------------------------------------------------------

    /** Só existe logo depois de um cadastro; aberta direto, volta ao início da administração. */
    @GetMapping("/concluido")
    String concluido(Model modelo) {
        return modelo.containsAttribute("concluido") ? "admin/concluido" : "redirect:/admin/usuarios";
    }

    // --------------------------------------------------------------------------------------------
    // Apoio
    // --------------------------------------------------------------------------------------------

    private String formularioDeUsuario(Model modelo, String nome, String email, Set<Perfil> marcados, String erro) {
        modelo.addAttribute("opcoesDePerfil", NomesDosPerfis.opcoes());
        modelo.addAttribute("nome", nome);
        modelo.addAttribute("emailNovo", email);
        modelo.addAttribute("perfisMarcados", marcados);
        modelo.addAttribute("erro", erro);
        return FORMULARIO_DE_USUARIO;
    }

    private String formularioDeTurma(Model modelo, DadosDaTurma turma, String erro) {
        modelo.addAttribute("turma", turma);
        modelo.addAttribute("erro", erro);
        return FORMULARIO_DE_TURMA;
    }

    private static Conclusao turmaSalva(Turma turma, String titulo) {
        return new Conclusao(
                "TURMA SALVA",
                titulo,
                "Somente informações gerais da turma foram registradas.",
                turma.anoSerie(),
                turma.rotulo(),
                turma.escola() + " · " + turma.turno() + " · sem lista de estudantes",
                "/admin/turmas",
                "Voltar para turmas");
    }

    /**
     * Executa a ação e redireciona com uma mensagem de sucesso ou de erro. Só trata os erros de
     * negócio e de validação; a falta de perfil (AccessDeniedException) continua virando 403.
     */
    private static String tratar(RedirectAttributes mensagens, String destino, String sucesso, Runnable acao) {
        try {
            acao.run();
            mensagens.addFlashAttribute("sucesso", sucesso);
        } catch (IllegalArgumentException | IllegalStateException
                | TurmaDuplicada | TurmaEmUso | TurmaNaoEncontrada erro) {
            mensagens.addFlashAttribute("erro", erro.getMessage());
        }
        return "redirect:" + destino;
    }

    private static Set<Perfil> ouVazio(Set<Perfil> perfis) {
        return perfis == null ? Set.of() : perfis;
    }

    private static String rotulos(Set<Perfil> perfis) {
        return List.of(Perfil.values()).stream()
                .filter(perfis::contains)
                .map(NomesDosPerfis::de)
                .collect(Collectors.joining(" · "));
    }

    private static BigDecimal paraValor(String texto) {
        return texto == null || texto.isBlank() ? null : new BigDecimal(texto.trim().replace(',', '.'));
    }

    /** O que o formulário de turma mostra, inclusive valores que ainda não passaram na validação. */
    record DadosDaTurma(Long id, String escola, String anoSerie, String turno, String rotulo) {

        static DadosDaTurma de(Turma turma) {
            return new DadosDaTurma(turma.id(), turma.escola(), turma.anoSerie(), turma.turno(), turma.rotulo());
        }

        public boolean editando() {
            return id != null;
        }
    }

    /** Conteúdo da tela de confirmação depois de um cadastro. */
    record Conclusao(
            String sobretitulo,
            String titulo,
            String texto,
            String etiqueta,
            String nome,
            String detalhe,
            String voltarPara,
            String voltarRotulo) {}
}
