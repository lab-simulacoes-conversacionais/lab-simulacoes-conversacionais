package br.ufg.inf.fixturesmodulith.autorizacao.semperfilnoutrometodo.aplicacao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class CasoDeUso {

    @PreAuthorize("hasRole('PROFESSOR')")
    public void protegido() {}

    public void esquecido() {}
}
