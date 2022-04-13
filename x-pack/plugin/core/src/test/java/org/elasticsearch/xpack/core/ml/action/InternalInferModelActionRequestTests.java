/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.action;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xpack.core.ml.AbstractBWCWireSerializationTestCase;
import org.elasticsearch.xpack.core.ml.action.InternalInferModelAction.Request;
import org.elasticsearch.xpack.core.ml.inference.MlInferenceNamedXContentProvider;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ClassificationConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.EmptyConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.FillMaskConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.FillMaskConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.InferenceConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.NerConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.NerConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.NlpConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.PassThroughConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.PassThroughConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.RegressionConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ResultsFieldUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.TextClassificationConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.TextClassificationConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.TextEmbeddingConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.TextEmbeddingConfigUpdateTests;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ZeroShotClassificationConfigUpdate;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ZeroShotClassificationConfigUpdateTests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InternalInferModelActionRequestTests extends AbstractBWCWireSerializationTestCase<Request> {

    @Override
    protected Request createTestInstance() {
        return randomBoolean()
            ? new Request(
                randomAlphaOfLength(10),
                Stream.generate(InternalInferModelActionRequestTests::randomMap).limit(randomInt(10)).collect(Collectors.toList()),
                randomInferenceConfigUpdate(),
                randomBoolean()
            )
            : new Request(randomAlphaOfLength(10), randomMap(), randomInferenceConfigUpdate(), randomBoolean());
    }

    private static InferenceConfigUpdate randomInferenceConfigUpdate() {
        return randomFrom(
            RegressionConfigUpdateTests.randomRegressionConfigUpdate(),
            ClassificationConfigUpdateTests.randomClassificationConfigUpdate(),
            ResultsFieldUpdateTests.randomUpdate(),
            TextClassificationConfigUpdateTests.randomUpdate(),
            TextEmbeddingConfigUpdateTests.randomUpdate(),
            NerConfigUpdateTests.randomUpdate(),
            FillMaskConfigUpdateTests.randomUpdate(),
            ZeroShotClassificationConfigUpdateTests.randomUpdate(),
            PassThroughConfigUpdateTests.randomUpdate(),
            EmptyConfigUpdateTests.testInstance()
        );
    }

    private static Map<String, Object> randomMap() {
        return Stream.generate(() -> randomAlphaOfLength(10))
            .limit(randomInt(10))
            .collect(Collectors.toMap(Function.identity(), (v) -> randomAlphaOfLength(10)));
    }

    @Override
    protected Writeable.Reader<Request> instanceReader() {
        return Request::new;
    }

    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(new MlInferenceNamedXContentProvider().getNamedWriteables());
        return new NamedWriteableRegistry(entries);
    }

    @Override
    protected Request mutateInstanceForVersion(Request instance, Version version) {
        InferenceConfigUpdate currentUpdate = instance.getUpdate();
        InferenceConfigUpdate adjustedUpdate = currentUpdate;
        if (adjustedUpdate instanceof NlpConfigUpdate nlpConfigUpdate) {
            adjustedUpdate = switch (nlpConfigUpdate) {
                case TextClassificationConfigUpdate update -> TextClassificationConfigUpdateTests.mutateForVersion(update, version);
                case TextEmbeddingConfigUpdate update -> TextEmbeddingConfigUpdateTests.mutateForVersion(update, version);
                case NerConfigUpdate update -> NerConfigUpdateTests.mutateForVersion(update, version);
                case FillMaskConfigUpdate update -> FillMaskConfigUpdateTests.mutateForVersion(update, version);
                case ZeroShotClassificationConfigUpdate update -> ZeroShotClassificationConfigUpdateTests.mutateForVersion(update, version);
                case PassThroughConfigUpdate update -> PassThroughConfigUpdateTests.mutateForVersion(update, version);
                default -> throw new IllegalArgumentException("Unknown update [" + currentUpdate.getName() + "]");
            };
        }
        return new Request(instance.getModelId(), instance.getObjectsToInfer(), adjustedUpdate, instance.isPreviouslyLicensed());
    }
}
