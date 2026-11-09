package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Importa o catálogo da BNCC a cada subida, para que o banco acompanhe os arquivos da versão em uso.
 * Como a importação não duplica nem altera o que já está igual, repetir não tem efeito.
 *
 * <p>Desligada com {@code bncc.importar-ao-iniciar=false} (variável BNCC_IMPORTAR_AO_INICIAR), como
 * nos testes. Se a importação falhar, a aplicação não sobe, porque os arquivos vêm com a própria
 * imagem e um erro neles precisa aparecer no deploy.
 */
@Component
@ConditionalOnProperty(name = "bncc.importar-ao-iniciar", havingValue = "true", matchIfMissing = true)
class ImportacaoNaSubida implements ApplicationRunner {

    private final ImportacaoDoCatalogo importacao;

    ImportacaoNaSubida(ImportacaoDoCatalogo importacao) {
        this.importacao = importacao;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        importacao.importar();
    }
}
