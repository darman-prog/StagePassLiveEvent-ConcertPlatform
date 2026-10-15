package com.stagepass.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * El aislamiento no es una promesa: es un hecho verificable. El dominio solo puede
 * depender del JDK; cualquier import de Spring, JDBC, Mongo o Lombok rompe esta prueba.
 */
class PruebaAislamientoArquitectonicoTest {

    // Se excluyen las clases de test: ellas si pueden importar JUnit/AssertJ.
    private final JavaClasses clasesDelDominio = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.stagepass.domain");

    @Test
    void elDominioSoloDependeDelJdk() {
        noClasses()
                .that().resideInAPackage("com.stagepass.domain..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "com.stagepass.domain..")
                .check(clasesDelDominio);
    }
}
