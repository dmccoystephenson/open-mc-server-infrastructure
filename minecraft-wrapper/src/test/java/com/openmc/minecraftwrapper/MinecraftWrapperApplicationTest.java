package com.openmc.minecraftwrapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "minecraft.auto.start=false"
})
class MinecraftWrapperApplicationTest {

    @Test
    void contextLoads() {
    }
}
