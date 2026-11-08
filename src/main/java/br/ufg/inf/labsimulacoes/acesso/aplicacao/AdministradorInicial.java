package br.ufg.inf.labsimulacoes.acesso.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuario;
import br.ufg.inf.labsimulacoes.acesso.dominio.Usuarios;
import br.ufg.inf.labsimulacoes.acesso.infraestrutura.PublicacaoDeEventos;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador ao subir, a partir de variáveis de ambiente. Só um administrador
 * cadastra usuários (RF-03), então sem isto ninguém conseguiria criar o primeiro.
 *
 * <p>Não faz nada se já existir um administrador ativo, então as variáveis podem ser removidas depois
 * do primeiro uso.
 */
@Component
class AdministradorInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdministradorInicial.class);
    private static final Set<Perfil> PERFIS = Set.of(Perfil.ADMINISTRADOR);

    private final Usuarios usuarios;
    private final CadastroDeUsuarios cadastro;
    private final PublicacaoDeEventos eventos;
    private final String email;
    private final String senha;

    AdministradorInicial(
            Usuarios usuarios,
            CadastroDeUsuarios cadastro,
            PublicacaoDeEventos eventos,
            @Value("${lab.administrador-inicial.email:}") String email,
            @Value("${lab.administrador-inicial.senha:}") String senha) {
        this.usuarios = usuarios;
        this.cadastro = cadastro;
        this.eventos = eventos;
        this.email = email;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        if (usuarios.contarAdministradoresAtivos() > 0) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            LOG.warn("Não há administrador ativo. Defina ADMIN_INICIAL_EMAIL e ADMIN_INICIAL_SENHA para criar um.");
            return;
        }
        if (usuarios.buscarPorEmail(email).isPresent()) {
            LOG.warn("Não há administrador ativo, e o e-mail de ADMIN_INICIAL_EMAIL já pertence a outro usuário.");
            return;
        }
        Usuario administrador = cadastro.cadastrar("Administrador", email, senha, PERFIS);
        // Ninguém está logado aqui, então o próprio administrador aparece como autor no log.
        eventos.perfilAlterado(administrador.id(), administrador.id(), Set.of(), administrador.perfis());
    }
}
