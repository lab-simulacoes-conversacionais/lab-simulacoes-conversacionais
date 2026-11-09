package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadesForaDoCatalogo;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação manual de cenários pelo professor ou curador (RF-06). O administrador não entra aqui, porque
 * não edita conteúdo pedagógico (RN-37).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class AutoriaDeCenarios {

    private final Cenarios cenarios;
    private final Habilidades habilidades;
    private final UsuarioLogado usuarioLogado;

    AutoriaDeCenarios(Cenarios cenarios, Habilidades habilidades, UsuarioLogado usuarioLogado) {
        this.cenarios = cenarios;
        this.habilidades = habilidades;
        this.usuarioLogado = usuarioLogado;
    }

    /** O rascunho pode estar incompleto: as regras de conteúdo mínimo valem no envio à curadoria. */
    @Transactional
    public Cenario criar(ConteudoDoCenario conteudo) {
        exigirHabilidadesDoCatalogo(conteudo);
        return cenarios.salvar(Cenario.novo(usuarioLogado.id(), conteudo));
    }

    /** O professor vê só os próprios cenários, e o curador vê todos (RN-37). */
    @Transactional(readOnly = true)
    @PostAuthorize("hasRole('CURADOR') or returnObject.temAutor(@usuarioLogado.id())")
    public Cenario buscar(long id) {
        return cenarios.buscar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
    }

    /** Só o autor edita o próprio rascunho (Figura 3 dos requisitos). */
    @Transactional
    public Cenario alterarRascunho(long id, ConteudoDoCenario conteudo) {
        Cenario cenario = cenarios.buscarParaAlterar(id).orElseThrow(() -> new CenarioNaoEncontrado(id));
        if (!cenario.temAutor(usuarioLogado.id())) {
            throw new AccessDeniedException("Só o autor edita o rascunho do cenário");
        }
        exigirHabilidadesDoCatalogo(conteudo);
        return cenarios.salvar(cenario.alterarRascunho(conteudo));
    }

    /** A chave estrangeira também recusaria, mas sem dizer ao autor qual código está errado. */
    private void exigirHabilidadesDoCatalogo(ConteudoDoCenario conteudo) {
        List<String> pedidas = conteudo.curriculo().habilidades();
        Set<String> existentes = habilidades.existentes(pedidas);
        List<String> desconhecidas = pedidas.stream().filter(codigo -> !existentes.contains(codigo)).toList();
        if (!desconhecidas.isEmpty()) {
            throw new HabilidadesForaDoCatalogo(desconhecidas);
        }
    }
}
