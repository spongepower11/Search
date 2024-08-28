/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.sql.jdbc;

import org.elasticsearch.Build;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.sql.proto.SqlVersion;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

public class DriverManagerRegistrationTests extends ESTestCase {

    public static final SqlVersion CURRENT = SqlVersion.fromString(Build.current().version());

    public void testRegistration() throws Exception {
        driverManagerTemplate(d -> assertNotNull(d));
    }

    public void testVersioning() throws Exception {
        driverManagerTemplate(d -> {
            /* This test will only work properly in gradle because in gradle we run the tests
             * using the jar. */

            assertNotEquals(String.valueOf(CURRENT.major), d.getMajorVersion());
            assertNotEquals(String.valueOf(CURRENT.minor), d.getMinorVersion());
        });
    }

    private static void driverManagerTemplate(Consumer<EsDriver> c) throws Exception {
        String url = "jdbc:es:localhost:9200/";
        Driver driver = null;
        try {
            // can happen (if the driver jar was not loaded)
            driver = DriverManager.getDriver(url);
        } catch (SQLException ex) {
            assertEquals("No suitable driver", ex.getMessage());
        }
        boolean set = driver != null;

        try {
            EsDriver d = EsDriver.register();
            if (driver != null) {
                assertEquals(driver, d);
            }

            c.accept(d);

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                // mimic DriverManager and unregister the driver
                EsDriver.deregister();
                return null;
            });

            SQLException ex = expectThrows(SQLException.class, () -> DriverManager.getDriver(url));
            assertEquals("No suitable driver", ex.getMessage());
        } finally {
            if (set) {
                EsDriver.register();
            }
        }
    }
}
