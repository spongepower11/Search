// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import java.lang.Boolean;
import java.lang.Override;
import java.lang.String;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.compute.ann.MvCombiner;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BooleanVector;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.BytesRefVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.operator.DriverContext;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.function.Warnings;

/**
 * {@link EvalOperator.ExpressionEvaluator} implementation for {@link SpatialDisjoint}.
 * This class is generated. Do not edit it.
 */
public final class SpatialDisjointCartesianPointDocValuesAndSourceEvaluator implements EvalOperator.ExpressionEvaluator {
  private final Warnings warnings;

  private final EvalOperator.ExpressionEvaluator leftValue;

  private final EvalOperator.ExpressionEvaluator rightValue;

  private final DriverContext driverContext;

  private final MvCombiner<Boolean> multiValuesCombiner = new AllCombiner();

  public SpatialDisjointCartesianPointDocValuesAndSourceEvaluator(Source source,
      EvalOperator.ExpressionEvaluator leftValue, EvalOperator.ExpressionEvaluator rightValue,
      DriverContext driverContext) {
    this.leftValue = leftValue;
    this.rightValue = rightValue;
    this.driverContext = driverContext;
    this.warnings = Warnings.createWarnings(driverContext.warningsMode(), source);
  }

  @Override
  public Block eval(Page page) {
    try (LongBlock leftValueBlock = (LongBlock) leftValue.eval(page)) {
      try (BytesRefBlock rightValueBlock = (BytesRefBlock) rightValue.eval(page)) {
        LongVector leftValueVector = leftValueBlock.asVector();
        if (leftValueVector == null) {
          return eval(page.getPositionCount(), leftValueBlock, rightValueBlock);
        }
        BytesRefVector rightValueVector = rightValueBlock.asVector();
        if (rightValueVector == null) {
          return eval(page.getPositionCount(), leftValueBlock, rightValueBlock);
        }
        return eval(page.getPositionCount(), leftValueVector, rightValueVector).asBlock();
      }
    }
  }

  public BooleanBlock eval(int positionCount, LongBlock leftValueBlock,
      BytesRefBlock rightValueBlock) {
    try(BooleanBlock.Builder result = driverContext.blockFactory().newBooleanBlockBuilder(positionCount)) {
      BytesRef rightValueScratch = new BytesRef();
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
        if (rightValueBlock.isNull(p)) {
          result.appendNull();
          continue position;
        }
        int rightValueBlockCount = rightValueBlock.getValueCount(p);
        if (rightValueBlockCount < 1) {
          result.appendNull();
          continue position;
        }
        int rightValueBlockFirst = rightValueBlock.getFirstValueIndex(p);
        Boolean mvResult = multiValuesCombiner.initial();
        for (int leftValueBlockIndex = leftValueBlockFirst; leftValueBlockIndex < leftValueBlockFirst + leftValueBlockCount; leftValueBlockIndex++) {
          for (int rightValueBlockIndex = rightValueBlockFirst; rightValueBlockIndex < rightValueBlockFirst + rightValueBlockCount; rightValueBlockIndex++) {
            mvResult = multiValuesCombiner.combine(mvResult, SpatialDisjoint.processCartesianPointDocValuesAndSource(leftValueBlock.getLong(leftValueBlock.getFirstValueIndex(p)), rightValueBlock.getBytesRef(rightValueBlockIndex, rightValueScratch)));
          }
        }
        result.appendBoolean(mvResult);
      }
      return result.build();
    }
  }

  public BooleanVector eval(int positionCount, LongVector leftValueVector,
      BytesRefVector rightValueVector) {
    try(BooleanVector.FixedBuilder result = driverContext.blockFactory().newBooleanVectorFixedBuilder(positionCount)) {
      BytesRef rightValueScratch = new BytesRef();
      position: for (int p = 0; p < positionCount; p++) {
        result.appendBoolean(p, SpatialDisjoint.processCartesianPointDocValuesAndSource(leftValueVector.getLong(p), rightValueVector.getBytesRef(p, rightValueScratch)));
      }
      return result.build();
    }
  }

  @Override
  public String toString() {
    return "SpatialDisjointCartesianPointDocValuesAndSourceEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + "]";
  }

  @Override
  public void close() {
    Releasables.closeExpectNoException(leftValue, rightValue);
  }

  static class Factory implements EvalOperator.ExpressionEvaluator.Factory {
    private final Source source;

    private final EvalOperator.ExpressionEvaluator.Factory leftValue;

    private final EvalOperator.ExpressionEvaluator.Factory rightValue;

    public Factory(Source source, EvalOperator.ExpressionEvaluator.Factory leftValue,
        EvalOperator.ExpressionEvaluator.Factory rightValue) {
      this.source = source;
      this.leftValue = leftValue;
      this.rightValue = rightValue;
    }

    @Override
    public SpatialDisjointCartesianPointDocValuesAndSourceEvaluator get(DriverContext context) {
      return new SpatialDisjointCartesianPointDocValuesAndSourceEvaluator(source, leftValue.get(context), rightValue.get(context), context);
    }

    @Override
    public String toString() {
      return "SpatialDisjointCartesianPointDocValuesAndSourceEvaluator[" + "leftValue=" + leftValue + ", rightValue=" + rightValue + "]";
    }
  }
}
