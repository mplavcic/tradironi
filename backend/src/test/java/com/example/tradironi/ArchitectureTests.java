package com.example.tradironi;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTests {

    private final ApplicationModules modules = ApplicationModules.of(TradironiApplication.class);

    @Test
    void moduleStructureIsValid() {
        modules.verify();
    }

    @Test
    void expectedModulesArePresent() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("shared", "user");
    }

    @Test
    void sharedModuleIsRegisteredAsShared() {
        assertThat(modules.getSharedModules())
                .extracting(module -> module.getIdentifier().toString())
                .containsExactlyInAnyOrder("shared");
    }

    @Test
    void userModuleHasNoDependenciesOnOtherModules() {
        ApplicationModule user = modules.getModuleByName("user").orElseThrow();

        assertThat(user.getDirectDependencies(modules).isEmpty()).isTrue();
    }

    @Test
    void sharedModuleDependsOnUserOnly() {
        ApplicationModule shared = modules.getModuleByName("shared").orElseThrow();

        assertThat(shared.getDirectDependencies(modules).uniqueModules())
                .extracting(module -> module.getIdentifier().toString())
                .containsExactly("user");
    }
}