package br.ufg.inf.labsimulacoes.auditoria.dominio;

/**
 * Com que perfil a pessoa sinalizou. Quem tem os dois perfis sinaliza como professor na própria sessão,
 * dentro do prazo, e como curador nos outros casos. O perfil importa depois da RF-25: só a sinalização
 * grave do professor suspende o cenário na hora (RN-23a).
 */
public enum PerfilDaSinalizacao {
    PROFESSOR,
    CURADOR
}
