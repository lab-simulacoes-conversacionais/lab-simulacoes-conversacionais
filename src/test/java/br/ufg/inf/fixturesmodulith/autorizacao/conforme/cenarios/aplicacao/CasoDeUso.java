package br.ufg.inf.fixturesmodulith.autorizacao.conforme.cenarios.aplicacao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CasoDeUso {

    @PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
    public void executar() {}

    void naoEPublico() {}
}
