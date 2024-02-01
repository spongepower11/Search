/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.elasticsearch.common.logging.Loggers.LOG_LEVEL_SETTING;
import static org.elasticsearch.core.Strings.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class LoggersTests extends ESTestCase {

    public void testLogLevelSettingRestrictions() {
        Settings settings = Settings.builder().put("logger.org.apache.http", "DEBUG").build();
        var ex = assertThrows(
            IllegalArgumentException.class,
            () -> LOG_LEVEL_SETTING.getConcreteSetting("logger.org.apache.http").get(settings)
        );
        assertThat(ex.getMessage(), containsString("Level [DEBUG] not permitted for logger [org.apache.http]"));
    }

    public void testSetLevelWithRestrictions() throws Exception {
        Logger apacheHttpClientLogger = LogManager.getLogger("org.apache.http.client.HttpClient");
        Logger apacheHttpLogger = LogManager.getLogger("org.apache.http");
        Logger apacheLogger = LogManager.getLogger("org.apache");

        List<String> restrictions = List.of(apacheHttpLogger.getName());

        Loggers.setLevel(apacheHttpLogger, Level.INFO, restrictions);
        assertHasINFO(apacheHttpLogger, apacheHttpClientLogger);

        for (Logger log : List.of(apacheHttpClientLogger, apacheHttpLogger)) {
            // DEBUG is rejected due to restriction
            Loggers.setLevel(log, Level.DEBUG, restrictions);
            assertHasINFO(apacheHttpClientLogger, apacheHttpLogger);
        }

        // OK for parent `org.apache`, but restriction is enforced for restricted descendants
        Loggers.setLevel(apacheLogger, Level.DEBUG, restrictions);
        assertEquals(Level.DEBUG, apacheLogger.getLevel());
        assertHasINFO(apacheHttpClientLogger, apacheHttpLogger);

        // Inheriting DEBUG of parent `org.apache` is rejected
        Loggers.setLevel(apacheHttpLogger, null, restrictions);
        assertHasINFO(apacheHttpClientLogger, apacheHttpLogger);

        // DEBUG of root logger isn't propagated to restricted loggers
        Loggers.setLevel(LogManager.getRootLogger(), Level.DEBUG, restrictions);
        assertEquals(Level.DEBUG, LogManager.getRootLogger().getLevel());
        assertHasINFO(apacheHttpClientLogger, apacheHttpLogger);
    }

    public void testStringSupplierAndFormatting() throws Exception {
        // adding a random id to allow test to run multiple times. See AbstractConfiguration#addAppender
        final MockAppender appender = new MockAppender("trace_appender" + randomInt());
        appender.start();
        final Logger testLogger = LogManager.getLogger(LoggersTests.class);
        Loggers.addAppender(testLogger, appender);
        Loggers.setLevel(testLogger, Level.TRACE);

        Throwable ex = randomException();
        testLogger.error(() -> format("an error message"), ex);
        assertThat(appender.lastEvent.getLevel(), equalTo(Level.ERROR));
        assertThat(appender.lastEvent.getThrown(), equalTo(ex));
        assertThat(appender.lastMessage().getFormattedMessage(), equalTo("an error message"));

        ex = randomException();
        testLogger.warn(() -> format("a warn message: [%s]", "long gc"), ex);
        assertThat(appender.lastEvent.getLevel(), equalTo(Level.WARN));
        assertThat(appender.lastEvent.getThrown(), equalTo(ex));
        assertThat(appender.lastMessage().getFormattedMessage(), equalTo("a warn message: [long gc]"));

        testLogger.info(() -> format("an info message a=[%s], b=[%s], c=[%s]", 1, 2, 3));
        assertThat(appender.lastEvent.getLevel(), equalTo(Level.INFO));
        assertThat(appender.lastEvent.getThrown(), nullValue());
        assertThat(appender.lastMessage().getFormattedMessage(), equalTo("an info message a=[1], b=[2], c=[3]"));

        ex = randomException();
        testLogger.debug(() -> format("a debug message options = %s", asList("yes", "no")), ex);
        assertThat(appender.lastEvent.getLevel(), equalTo(Level.DEBUG));
        assertThat(appender.lastEvent.getThrown(), equalTo(ex));
        assertThat(appender.lastMessage().getFormattedMessage(), equalTo("a debug message options = [yes, no]"));

        ex = randomException();
        testLogger.trace(() -> format("a trace message; element = [%s]", new Object[] { null }), ex);
        assertThat(appender.lastEvent.getLevel(), equalTo(Level.TRACE));
        assertThat(appender.lastEvent.getThrown(), equalTo(ex));
        assertThat(appender.lastMessage().getFormattedMessage(), equalTo("a trace message; element = [null]"));
    }

    private Throwable randomException() {
        return randomFrom(
            new IOException("file not found"),
            new UnknownHostException("unknown hostname"),
            new OutOfMemoryError("out of space"),
            new IllegalArgumentException("index must be between 10 and 100")
        );
    }

    private static void assertHasINFO(Logger... loggers) {
        for (Logger log : loggers) {
            assertEquals("Log level of [" + log.getName() + "]", Level.INFO, log.getLevel());
        }
    }
}
