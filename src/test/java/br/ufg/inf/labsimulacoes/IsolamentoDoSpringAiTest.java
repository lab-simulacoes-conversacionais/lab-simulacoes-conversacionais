package br.ufg.inf.labsimulacoes;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Independência de provedor (RNF-02): só a infraestrutura do Adaptador de IA conhece o Spring AI. Os
 * outros módulos, e as outras camadas do próprio adaptador, falam só com a porta do módulo.
 */
class IsolamentoDoSpringAiTest {

    private static final String SISTEMA = "br.ufg.inf.labsimulacoes";
    private static final String FIXTURES = "br.ufg.inf.fixturesmodulith.springai";

    private static final ArchRule SO_A_INFRAESTRUTURA_DO_ADAPTADOR_USA_O_SPRING_AI = noClasses()
            .that().resideOutsideOfPackage("..ia.infraestrutura..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")
            .allowEmptyShould(true);

    @Test
    void sistemaIsolaOSpringAiNoAdaptador() {
        SO_A_INFRAESTRUTURA_DO_ADAPTADOR_USA_O_SPRING_AI.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(SISTEMA));
    }

    @Test
    void usoNaInfraestruturaDoAdaptadorPassa() {
        assertThat(SO_A_INFRAESTRUTURA_DO_ADAPTADOR_USA_O_SPRING_AI.evaluate(fixture("conforme")).hasViolation())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foradoia", "naaplicacaodoia"})
    void usoForaDaInfraestruturaDoAdaptadorFalha(String cenario) {
        assertThat(SO_A_INFRAESTRUTURA_DO_ADAPTADOR_USA_O_SPRING_AI.evaluate(fixture(cenario)).hasViolation())
                .isTrue();
    }

    private static JavaClasses fixture(String cenario) {
        return new ClassFileImporter().importPackages(FIXTURES + "." + cenario);
    }
}
