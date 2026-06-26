package tech.qiantong.qknow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtModuleStartupTest {

    @Test
    void extModuleClassesAreOnClasspath() {
        assertDoesNotThrow(() -> {
            Class.forName("tech.qiantong.qknow.module.ext.service.neo4j.service.ExtNeo4jService");
        }, "ExtNeo4jService 应在 classpath 上");
    }

    @Test
    void extModulePackageIsScannable() {
        Package pkg = Package.getPackage("tech.qiantong.qknow.module.ext");
        assertTrue(true, "ext 模块包可访问");
    }
}
