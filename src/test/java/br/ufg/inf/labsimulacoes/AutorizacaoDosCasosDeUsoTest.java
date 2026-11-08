package br.ufg.inf.labsimulacoes;

import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Autorização por perfil em cada caso de uso (Stack, seção 7) e administrador fora do conteúdo
 * pedagógico (RN-37). As funções dos outros módulos ainda não existem, então estas regras garantem
 * que elas nasçam protegidas: o build falha se um caso de uso público não declarar quais perfis o usam.
 */
class AutorizacaoDosCasosDeUsoTest {

    private static final String SISTEMA = "br.ufg.inf.labsimulacoes";
    private static final String FIXTURES = "br.ufg.inf.fixturesmodulith.autorizacao";

    /** O cadastro não é um caso de uso: a {@code GestaoDeUsuarios} é quem verifica o perfil. */
    private static final String SEM_VERIFICACAO = "CadastroDeUsuarios";

    private static final DescribedPredicate<JavaMethod> DE_CASO_DE_USO =
            DescribedPredicate.describe("públicos de um caso de uso", metodo -> {
                var classe = metodo.getOwner();
                return metodo.getModifiers().contains(JavaModifier.PUBLIC)
                        && !metodo.getModifiers().contains(JavaModifier.SYNTHETIC)
                        && classe.getModifiers().contains(JavaModifier.PUBLIC)
                        && classe.isAnnotatedWith(Service.class)
                        && classe.getPackageName().contains(".aplicacao")
                        && !classe.getSimpleName().equals(SEM_VERIFICACAO);
            });

    private static final ArchRule TODO_CASO_DE_USO_VERIFICA_O_PERFIL = methods()
            .that(DE_CASO_DE_USO)
            .should(new ArchCondition<JavaMethod>("declarar @PreAuthorize no método ou na classe") {
                @Override
                public void check(JavaMethod metodo, ConditionEvents eventos) {
                    boolean declara = metodo.isAnnotatedWith(PreAuthorize.class)
                            || metodo.getOwner().isAnnotatedWith(PreAuthorize.class);
                    eventos.add(new SimpleConditionEvent(metodo, declara, metodo.getFullName()));
                }
            })
            .allowEmptyShould(true);

    private static final DescribedPredicate<JavaMethod> DE_CONTEUDO_PEDAGOGICO =
            DescribedPredicate.describe(
                    "do módulo de cenários, que guarda o conteúdo pedagógico",
                    metodo -> metodo.getOwner().getPackageName().matches(".*\\.cenarios(\\..*)?"));

    private static final ArchRule ADMINISTRADOR_FORA_DO_CONTEUDO = methods()
            .that(DE_CONTEUDO_PEDAGOGICO)
            .should(not(new ArchCondition<JavaMethod>("liberar o perfil Administrador") {
                @Override
                public void check(JavaMethod metodo, ConditionEvents eventos) {
                    eventos.add(new SimpleConditionEvent(metodo, liberaAdministrador(metodo), metodo.getFullName()));
                }
            }))
            .allowEmptyShould(true);

    private static boolean liberaAdministrador(JavaMethod metodo) {
        return mencionaAdministrador(metodo.tryGetAnnotationOfType(PreAuthorize.class))
                || mencionaAdministrador(metodo.getOwner().tryGetAnnotationOfType(PreAuthorize.class));
    }

    private static boolean mencionaAdministrador(java.util.Optional<PreAuthorize> anotacao) {
        return anotacao.map(PreAuthorize::value).orElse("").contains("ADMINISTRADOR");
    }

    @Test
    void sistemaVerificaOPerfilEmCadaCasoDeUso() {
        TODO_CASO_DE_USO_VERIFICA_O_PERFIL.check(sistema());
    }

    @Test
    void administradorNaoTemAcessoAoConteudoPedagogico() {
        ADMINISTRADOR_FORA_DO_CONTEUDO.check(sistema());
    }

    @Test
    void casosDeUsoConformesPassam() {
        JavaClasses conforme = fixture("conforme");

        assertThat(TODO_CASO_DE_USO_VERIFICA_O_PERFIL.evaluate(conforme).hasViolation()).isFalse();
        assertThat(ADMINISTRADOR_FORA_DO_CONTEUDO.evaluate(conforme).hasViolation()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"semperfil", "semperfilnoutrometodo"})
    void casoDeUsoSemPerfilFalha(String cenario) {
        assertThat(TODO_CASO_DE_USO_VERIFICA_O_PERFIL.evaluate(fixture(cenario)).hasViolation()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"adminnometodo", "adminnaclasse"})
    void conteudoPedagogicoLiberadoAoAdministradorFalha(String cenario) {
        assertThat(ADMINISTRADOR_FORA_DO_CONTEUDO.evaluate(fixture(cenario)).hasViolation()).isTrue();
    }

    private static JavaClasses sistema() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(SISTEMA);
    }

    private static JavaClasses fixture(String cenario) {
        return new ClassFileImporter().importPackages(FIXTURES + "." + cenario);
    }
}
