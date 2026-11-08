package br.ufg.inf.fixturesmodulith.autorizacao.adminnometodo.cenarios.aplicacao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CasoDeUso {

    @PreAuthorize("hasAnyRole('PROFESSOR', 'ADMINISTRADOR')")
    public void editar() {}
}
