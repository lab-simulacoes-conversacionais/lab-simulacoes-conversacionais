package br.ufg.inf.fixturesmodulith.autorizacao.adminnaclasse.cenarios.aplicacao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CasoDeUso {

    public void editar() {}
}
