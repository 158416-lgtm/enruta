package com.enruta.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void addRoleAddsToTheExistingRoles() {
        User user = new User("ana@enruta.test", "hash", "Ana", EnumSet.of(Role.CLIENT));

        user.addRole(Role.DRIVER);

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.CLIENT, Role.DRIVER);
    }

    @Test
    void jpaConstructorIsAvailableViaReflection() throws Exception {
        var constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
