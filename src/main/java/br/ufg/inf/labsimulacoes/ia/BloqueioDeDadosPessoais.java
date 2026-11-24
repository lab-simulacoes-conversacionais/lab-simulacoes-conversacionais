package br.ufg.inf.labsimulacoes.ia;

/**
 * Bloqueio de dados pessoais antes do envio ao provedor (RF-21, RN-13, RNF-01). Cada caso de uso que
 * manda ao provedor um texto digitado chama este bloqueio antes de qualquer chamada, inclusive a da
 * moderação, porque a moderação também envia o texto. A porta {@link AdaptadorDeIa} confere de novo
 * a pergunta e o tema, como segunda barreira.
 *
 * <p>Cada bloqueio conta na métrica {@code lab.privacidade.bloqueio}, com o tipo como única tag. Nada
 * vai para log, evento ou auditoria.
 *
 * <p>Exige o perfil Professor ou Curador, como o adaptador.
 */
public interface BloqueioDeDadosPessoais {

    /**
     * Confere o texto pelos padrões de {@link DadosPessoais}. Um texto vazio passa: recusá-lo é de quem
     * chama.
     *
     * @throws TextoComDadoPessoal se houver e-mail, telefone, CPF ou endereço
     */
    void exigirSemDadoPessoal(String texto);
}
