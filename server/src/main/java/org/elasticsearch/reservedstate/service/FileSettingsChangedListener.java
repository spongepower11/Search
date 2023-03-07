/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.reservedstate.service;

/**
 * Listener interface for the file settings service. Listeners will get
 * notified when the settings have been updated, or if there are no settings
 * on initial start.
 */
// TODO[wrb]: rename for any watched file?
public interface FileSettingsChangedListener {
    void settingsChanged();
}
