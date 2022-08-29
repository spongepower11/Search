/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins.scanners;

import org.elasticsearch.plugin.api.Extensible;
import org.elasticsearch.plugins.scanners.extensible_test_classes.ExtensibleClass;
import org.elasticsearch.plugins.scanners.extensible_test_classes.ExtensibleInterface;
import org.elasticsearch.plugins.scanners.extensible_test_classes.ImplementingExtensible;
import org.elasticsearch.plugins.scanners.extensible_test_classes.SubClass;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;
import org.objectweb.asm.ClassReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

public class AnnotatedHierarchyVisitorTests extends ESTestCase {
    Set<String> foundClasses = new HashSet<>();
    AnnotatedHierarchyVisitor visitor = new AnnotatedHierarchyVisitor(Extensible.class, className -> {
        foundClasses.add(className);
        return null;
    });

    public void testNotAnnotatedClass() throws IOException {
        performScan(visitor, AnnotatedHierarchyVisitorTests.class);

        assertThat(foundClasses, Matchers.emptyCollectionOf(String.class));
    }

    public void testAnnotatedClass() throws IOException {
        performScan(visitor, ExtensibleClass.class);

        assertThat(foundClasses, contains(classNameToPath(ExtensibleClass.class)));
    }

    public void testClassHierarchy() throws IOException {
        performScan(visitor, ExtensibleClass.class, SubClass.class);

        assertThat(foundClasses, contains(classNameToPath(ExtensibleClass.class)));

        assertThat(
            visitor.getClassHierarchy(),
            equalTo(Map.of(classNameToPath(ExtensibleClass.class), Set.of(classNameToPath(SubClass.class))))
        );
    }

    public void testInterfaceHierarchy() throws IOException {
        performScan(visitor, ImplementingExtensible.class, ExtensibleInterface.class);

        assertThat(foundClasses, contains(classNameToPath(ExtensibleInterface.class)));

        assertThat(
            visitor.getClassHierarchy(),
            equalTo(Map.of(classNameToPath(ExtensibleInterface.class), Set.of(classNameToPath(ImplementingExtensible.class))))
        );
    }

    private String classNameToPath(Class<?> clazz) {
        return clazz.getCanonicalName().replace(".", "/");
    }

    private void performScan(AnnotatedHierarchyVisitor classVisitor, Class<?>... classes) throws IOException {
        String mainPath = AnnotatedHierarchyVisitorTests.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        for (Class<?> clazz : classes) {
            String className = classNameToPath(clazz) + ".class";
            Path path = Path.of(mainPath, className);
            FileInputStream fileInputStream = new FileInputStream(path.toFile());
            ClassReader cr = new ClassReader(fileInputStream);
            cr.accept(classVisitor, 0);
        }
    }

}
