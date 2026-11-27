package br.ufg.inf.labsimulacoes.cenarios.dominio;

/**
 * Com que perfil a pessoa sinalizou. Quem tem os dois perfis sinaliza com um deles, e só a sinalização grave
 * feita como professor suspende o cenário na hora (RN-23a).
 */
public enum PerfilDeQuemSinalizou {
    PROFESSOR,
    CURADOR
}
