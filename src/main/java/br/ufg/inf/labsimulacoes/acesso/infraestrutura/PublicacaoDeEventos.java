package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.eventos.PerfilAlterado;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publica os eventos do módulo (Stack, seção 4), traduzindo o domínio para valores simples. */
@Component
public class PublicacaoDeEventos {

    private final ApplicationEventPublisher publicador;

    PublicacaoDeEventos(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    public void perfilAlterado(long usuarioId, long alteradoPorId, Set<Perfil> anteriores, Set<Perfil> novos) {
        publicador.publishEvent(
                new PerfilAlterado(usuarioId, alteradoPorId, nomes(anteriores), nomes(novos), Instant.now()));
    }

    private static Set<String> nomes(Set<Perfil> perfis) {
        return perfis.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
