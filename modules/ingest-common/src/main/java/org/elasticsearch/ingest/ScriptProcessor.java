/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.ingest;

import org.elasticsearch.ingest.core.AbstractProcessor;
import org.elasticsearch.ingest.core.AbstractProcessorFactory;
import org.elasticsearch.ingest.core.ConfigurationUtils;
import org.elasticsearch.ingest.core.IngestDocument;
import org.elasticsearch.ingest.core.TemplateService;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Processor that adds new fields with their corresponding values. If the field is already present, its value
 * will be replaced with the provided one.
 */
public final class ScriptProcessor extends AbstractProcessor {

    public static final String TYPE = "script";

    private final TemplateService.Template template;
    private final ScriptService scriptService;
    private final String scriptLang;
    private final String returnField;

    ScriptProcessor(String tag, TemplateService.Template template, ScriptService scriptService, String scriptLang, String returnField)  {
        super(tag);
        this.template = template;
        this.scriptService = scriptService;
        this.scriptLang = scriptLang;
        this.returnField = returnField;
    }

    @Override
    public void execute(IngestDocument document) {
        String rawScript = document.renderTemplate(template);
        Map<String, Object> vars = new HashMap<>();
        vars.put("doc", document.getSourceAndMetadata());

        Script script = new Script(rawScript, ScriptService.ScriptType.INLINE, scriptLang, Collections.emptyMap());
        CompiledScript compiledScript = scriptService.compile(script, ScriptContext.Standard.INGEST, Collections.emptyMap(), null);
        ExecutableScript executableScript = scriptService.executable(compiledScript, vars);
        Object value = executableScript.run();
        if (returnField != null) {
            document.setFieldValue(returnField, value);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory extends AbstractProcessorFactory<ScriptProcessor> {

        private final TemplateService templateService;

        public Factory(TemplateService templateService) {
            this.templateService = templateService;
        }

        @Override
        public ScriptProcessor doCreate(String processorTag, Map<String, Object> config) throws Exception {
            String script = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, "script");
            String scriptLang = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, "lang");
            String returnField = ConfigurationUtils.readOptionalStringProperty(TYPE, processorTag, config, "return_field");
            ScriptService scriptService = templateService.getScriptService();
            return new ScriptProcessor(processorTag, templateService.compile(script), scriptService, scriptLang, returnField);
        }
    }
}
