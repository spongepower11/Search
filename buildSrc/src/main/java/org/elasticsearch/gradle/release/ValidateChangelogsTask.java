/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.release;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;

public class ValidateChangelogsTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(ValidateChangelogsTask.class);

    private final ConfigurableFileCollection changelogs = getProject().getObjects().fileCollection();

    @InputFiles
    public FileCollection getChangelogs() {
        return changelogs;
    }

    @TaskAction
    public void executeTask() {
        LOGGER.info("Finding and validating changelog files...");

        for (File file : this.changelogs.getFiles()) {
            ChangelogEntry.parseChangelog(file);
        }
    }

    public void setChangelogs(Set<File> files) {
        this.changelogs.setFrom(files);
    }
}
