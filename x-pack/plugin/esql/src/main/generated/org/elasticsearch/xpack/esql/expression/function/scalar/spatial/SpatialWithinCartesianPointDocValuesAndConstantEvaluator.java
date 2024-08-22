// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import java.lang.Boolean;
import java.lang.IllegalArgumentException;
import java.lang.Override;
import java.lang.String;
import org.apache.lucene.geo.Component2D;
import org.elasticsearch.compute.ann.MvCombiner;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.function.Warnings;

/**
 * {@link EvalOperator.ExpressionEvaluator} implementation for {@link SpatialWithin}.
 * This class is generated. Do not edit it.
 */
public final class SpatialWithinCartesianPointDocValuesAndConstantEvaluator implements EvalOperator.ExpressionEvaluator {
  private final Warnings warnings;

  private final EvalOperator.ExpressionEvaluator leftValue;

  private final Component2D rightValue;

  private final DriverContext driverContext;

  private final MvCombiner<Boolean> multiValuesCombiner = new AllCombiner();

  public SpatialWithinCartesianPointDocValuesAndConstantEvaluator(Source source,
      EvalOperator.ExpressionEvaluator leftValue, Component2D rightValue,
      DriverContext driverContext) {
    this.leftValue = leftValue;
    this.rightValue = rightValue;
    this.driverContext = driverContext;
    this.warnings = Warnings.createWarnings(driverContext.warningsMode(), source);
  }

  @Override
  public Block eval(Page page) {
    try (LongBlock leftValueBlock = (LongBlock) leftValue.eval(page)) {
      LongVector leftValueVector = leftValueBlock.asVector();
      if (leftValueVector == null) {
        return eval(page.getPositionCount(), leftValueBlock);
      }
      return eval(page.getPositionCount(), leftValueVector);
    }
  }

  public BooleanBlock eval(int positionCount, LongBlock leftValueBlock) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        if (leftValueBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        int leftValueBlockCount = leftValueBlock.getValueCount(p);
        if (leftValueBlockCount < 1) {
          result.appendNull();
          continue position;
        }
        int leftValueBlockFirst = leftValueBlock.getFirstValueIndex(p);
        try {
          Boolean mvResult = multiValuesCombiner.initial();
          for (int leftValueBlockIndex = leftValueBlockFirst; leftValueBlockIndex < leftValueBlockFirst + leftValueBlockCount; leftValueBlockIndex++) {
            mvResult = multiValuesCombiner.combine(mvResult, SpatialWithin.processCartesianPointDocValuesAndConstant(leftValueBlock.getLong(leftValueBlock.getFirstValueIndex(p)), this.rightValue));
          }
          result.appendBoolean(mvResult);
        } catch (IllegalArgumentException e) {
          warnings.registerException(e);
          result.appendNull();
        }
      }
      return result.build();
    }
  }

  public BooleanBlock eval(int positionCount, LongVector leftValueVector) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      position: for (int p = 0; p < positionCount; p++) {
        try {
          result.appendBoolean(SpatialWithin.processCartesianPointDocValuesAndConstant(leftValueVector.getLong(p), this.rightValue));
        } catch (IllegalArgumentException e) {
          warnings.registerException(e);
          result.appendNull();
        }
      }
      return result.build();
    }
  }

  @Override
  public String toString() {
    return "SpatialWithinCartesianPointDocValuesAndConstantEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + "]";
  }

  @Override
  public void close() {
    Releasables.closeExpectNoException(leftValue);
  }

  static class Factory implements EvalOperator.ExpressionEvaluator.Factory {
    private final Source source;

    private final EvalOperator.ExpressionEvaluator.Factory leftValue;

    private final Component2D rightValue;

    public Factory(Source source, EvalOperator.ExpressionEvaluator.Factory leftValue,
        Component2D rightValue) {
      this.source = source;
      this.leftValue = leftValue;
      this.rightValue = rightValue;
    }

    @Override
    public SpatialWithinCartesianPointDocValuesAndConstantEvaluator get(DriverContext context) {
      return new SpatialWithinCartesianPointDocValuesAndConstantEvaluator(source, leftValue.get(context), rightValue, context);
    }

    @Override
    public String toString() {
      return "SpatialWithinCartesianPointDocValuesAndConstantEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + "]";
    }
  }
}
