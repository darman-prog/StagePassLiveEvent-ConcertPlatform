package com.stagepass.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * El aislamiento no es una promesa: es un hecho verificable. El dominio solo puede
 * depender del JDK; los puertos y los casos de uso, solo del nucleo. Cualquier import
 * de Spring, JDBC, Mongo o Lombok rompe estas pruebas.
 */
class PruebaAislamientoArquitectonicoTest {

    // Se importa todo el codigo principal y se dejan fuera los tests: ellos si pueden usar JUnit y AssertJ.
    private final JavaClasses clases = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.stagepass");

    @Test
    void elDominioSoloDependeDelJdk() {
        noClasses()
                .that().resideInAPackage("com.stagepass.domain..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "com.stagepass.domain..")
                .check(clases);
    }

    @Test
    void losPuertosSoloDependenDelNucleo() {
        // Ningun puerto puede mencionar java.sql, Spring ni ningun SDK: seria tecnologia filtrandose.
        noClasses()
                .that().resideInAPackage("com.stagepass.ports..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "com.stagepass.domain..", "com.stagepass.ports..")
                .check(clases);
    }

    @Test
    void losCasosDeUsoSoloDependenDelNucleo() {
        noClasses()
                .that().resideInAPackage("com.stagepass.application..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "com.stagepass.domain..",
                        "com.stagepass.ports..", "com.stagepass.application..")
                .check(clases);
    }
}
