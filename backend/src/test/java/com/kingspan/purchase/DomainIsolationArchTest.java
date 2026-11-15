package com.kingspan.purchase;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

@AnalyzeClasses(packages = "com.kingspan.purchase", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainIsolationArchTest {

    private static final Set<String> ALLOWED_SHARED_TYPES =
            Set.of(BusinessRuleException.class.getName(), ErrorCode.class.getName());

    @ArchTest
    static final ArchRule dominioNaoDeveDependerDeFrameworksNemDePersistencia = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax.persistence..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..",
                    "tools.jackson..")
            .because("o domínio é Java puro e testável sem subir servidor ou banco (README, seção 5)");

    @ArchTest
    static final ArchRule dominioNaoDeveDependerDasCamadasAoRedor = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infra..", "..web..", "..config..")
            .because("as dependências apontam para dentro: web → application → domain");

    @ArchTest
    static final ArchRule dominioSoDeveDependerDoJavaEDeOutrosDominios = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat(resideInAnyPackage("java..", "..domain..").or(allowedSharedType()))
            .because("uma whitelist pega dependências novas que a lista de proibidos não previu");

    // Tipo base para aceitar também o ErrorCode[] gerado pelo values() do enum.
    private static DescribedPredicate<JavaClass> allowedSharedType() {
        return describe(
                "BusinessRuleException ou ErrorCode",
                type -> ALLOWED_SHARED_TYPES.contains(
                        type.getBaseComponentType().getName()));
    }

    @ArchTest
    static final ArchRule excecaoDeRegraDeNegocioDeveSerJavaPuro = classes()
            .that()
            .haveFullyQualifiedName(BusinessRuleException.class.getName())
            .or()
            .haveFullyQualifiedName(ErrorCode.class.getName())
            .should()
            .onlyDependOnClassesThat(resideInAnyPackage("java..").or(allowedSharedType()))
            .because("o domínio depende delas, então não podem trazer o framework junto");
}
