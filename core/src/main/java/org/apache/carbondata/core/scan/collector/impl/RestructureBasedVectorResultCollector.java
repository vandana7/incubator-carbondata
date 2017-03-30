/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.core.scan.collector.impl;

import java.math.BigDecimal;
import java.util.List;

import org.apache.carbondata.core.metadata.encoder.Encoding;
import org.apache.carbondata.core.metadata.schema.table.column.CarbonDimension;
import org.apache.carbondata.core.metadata.schema.table.column.CarbonMeasure;
import org.apache.carbondata.core.scan.executor.infos.BlockExecutionInfo;
import org.apache.carbondata.core.scan.executor.util.RestructureUtil;
import org.apache.carbondata.core.scan.result.AbstractScannedResult;
import org.apache.carbondata.core.scan.result.vector.CarbonColumnVector;
import org.apache.carbondata.core.scan.result.vector.CarbonColumnarBatch;
import org.apache.carbondata.core.scan.result.vector.ColumnVectorInfo;

import org.apache.spark.sql.types.Decimal;

/**
 * It is not a collector it is just a scanned result holder.
 */
public class RestructureBasedVectorResultCollector extends DictionaryBasedVectorResultCollector {

  public RestructureBasedVectorResultCollector(BlockExecutionInfo blockExecutionInfos) {
    super(blockExecutionInfos);
    queryDimensions = tableBlockExecutionInfos.getActualQueryDimensions();
    queryMeasures = tableBlockExecutionInfos.getActualQueryMeasures();
    allColumnInfo = new ColumnVectorInfo[queryDimensions.length + queryMeasures.length];
    createVectorForNewlyAddedDimensions();
    createVectorForNewlyAddedMeasures();
    prepareDimensionAndMeasureColumnVectors();
  }

  /**
   * create column vector for newly added dimension columns
   */
  private void createVectorForNewlyAddedDimensions() {
    for (int i = 0; i < queryDimensions.length; i++) {
      if (!dimensionInfo.getDimensionExists()[i]) {
        // add a dummy column vector result collector object
        ColumnVectorInfo columnVectorInfo = new ColumnVectorInfo();
        allColumnInfo[queryDimensions[i].getQueryOrder()] = columnVectorInfo;
      }
    }
  }

  /**
   * create column vector for newly added measure columns
   */
  private void createVectorForNewlyAddedMeasures() {
    for (int i = 0; i < queryMeasures.length; i++) {
      if (!measureInfo.getMeasureExists()[i]) {
        // add a dummy column vector result collector object
        ColumnVectorInfo columnVectorInfo = new ColumnVectorInfo();
        allColumnInfo[queryMeasures[i].getQueryOrder()] = columnVectorInfo;
      }
    }
  }

  @Override public List<Object[]> collectData(AbstractScannedResult scannedResult, int batchSize) {
    throw new UnsupportedOperationException("collectData is not supported here");
  }

  @Override public void collectVectorBatch(AbstractScannedResult scannedResult,
      CarbonColumnarBatch columnarBatch) {
    int numberOfPages = scannedResult.numberOfpages();
    while (scannedResult.getCurrentPageCounter() < numberOfPages) {
      int currentPageRowCount = scannedResult.getCurrentPageRowCount();
      if (currentPageRowCount == 0) {
        scannedResult.incrementPageCounter();
        continue;
      }
      int rowCounter = scannedResult.getRowCounter();
      int availableRows = currentPageRowCount - rowCounter;
      int requiredRows = columnarBatch.getBatchSize() - columnarBatch.getActualSize();
      requiredRows = Math.min(requiredRows, availableRows);
      if (requiredRows < 1) {
        return;
      }
      fillColumnVectorDetails(columnarBatch, rowCounter, requiredRows);
      // fill default values for non existing dimensions and measures
      fillDataForNonExistingDimensions();
      fillDataForNonExistingMeasures();
      // fill existing dimensions and measures data
      scanAndFillResult(scannedResult, columnarBatch, rowCounter, availableRows, requiredRows);
    }
  }

  /**
   * This method will fill the default values of non existing dimensions in the current block
   */
  private void fillDataForNonExistingDimensions() {
    for (int i = 0; i < tableBlockExecutionInfos.getActualQueryDimensions().length; i++) {
      if (!dimensionInfo.getDimensionExists()[i]) {
        CarbonDimension dimension =
            tableBlockExecutionInfos.getActualQueryDimensions()[i].getDimension();
        if (dimension.hasEncoding(Encoding.DIRECT_DICTIONARY)) {
          // fill direct dictionary column data
          fillDirectDictionaryData(allColumnInfo[i].vector, allColumnInfo[i],
              dimensionInfo.getDefaultValues()[i]);
        } else if (dimension.hasEncoding(Encoding.DICTIONARY)) {
          // fill dictionary column data
          fillDictionaryData(allColumnInfo[i].vector, allColumnInfo[i],
              dimensionInfo.getDefaultValues()[i]);
        } else {
          // fill no dictionary data
          fillNoDictionaryData(allColumnInfo[i].vector, allColumnInfo[i],
              dimension.getDefaultValue());
        }
      }
    }
  }

  /**
   * This method will fill the dictionary column data
   *
   * @param vector
   * @param columnVectorInfo
   * @param defaultValue
   */
  private void fillDictionaryData(CarbonColumnVector vector, ColumnVectorInfo columnVectorInfo,
      Object defaultValue) {
    vector.putInts(columnVectorInfo.vectorOffset, columnVectorInfo.size, (int) defaultValue);
  }

  /**
   * This method will fill the direct dictionary column data
   *
   * @param vector
   * @param columnVectorInfo
   * @param defaultValue
   */
  private void fillDirectDictionaryData(CarbonColumnVector vector,
      ColumnVectorInfo columnVectorInfo, Object defaultValue) {
    if (null != defaultValue) {
      vector.putLongs(columnVectorInfo.vectorOffset, columnVectorInfo.size, (long) defaultValue);
    } else {
      vector.putNulls(columnVectorInfo.vectorOffset, columnVectorInfo.size);
    }
  }

  /**
   * This method will fill the no dictionary column data
   *
   * @param vector
   * @param columnVectorInfo
   * @param defaultValue
   */
  private void fillNoDictionaryData(CarbonColumnVector vector, ColumnVectorInfo columnVectorInfo,
      byte[] defaultValue) {
    if (null != defaultValue) {
      vector.putBytes(columnVectorInfo.vectorOffset, columnVectorInfo.size, defaultValue);
    } else {
      vector.putNulls(columnVectorInfo.vectorOffset, columnVectorInfo.size);
    }
  }

  /**
   * This method will fill the default values of non existing measures in the current block
   */
  private void fillDataForNonExistingMeasures() {
    for (int i = 0; i < tableBlockExecutionInfos.getActualQueryMeasures().length; i++) {
      if (!measureInfo.getMeasureExists()[i]) {
        CarbonMeasure measure = tableBlockExecutionInfos.getActualQueryMeasures()[i].getMeasure();
        ColumnVectorInfo columnVectorInfo = allColumnInfo[i];
        CarbonColumnVector vector = allColumnInfo[i].vector;
        Object defaultValue = RestructureUtil
            .getMeasureDefaultValue(measure.getColumnSchema(), measure.getDefaultValue());
        if (null == defaultValue) {
          vector.putNulls(columnVectorInfo.vectorOffset, columnVectorInfo.size);
        } else {
          switch (measureInfo.getMeasureDataTypes()[i]) {
            case SHORT:
              vector.putShorts(columnVectorInfo.vectorOffset, columnVectorInfo.size,
                  (short) defaultValue);
              break;
            case INT:
              vector.putInts(columnVectorInfo.vectorOffset, columnVectorInfo.size,
                  (int) defaultValue);
              break;
            case LONG:
              vector.putLongs(columnVectorInfo.vectorOffset, columnVectorInfo.size,
                  (long) defaultValue);
              break;
            case DECIMAL:
              Decimal convertToSparkType = Decimal.apply((BigDecimal) defaultValue);
              vector.putDecimals(columnVectorInfo.vectorOffset, columnVectorInfo.size,
                  convertToSparkType, measure.getPrecision());
              break;
            default:
              vector.putDoubles(columnVectorInfo.vectorOffset, columnVectorInfo.size,
                  (double) defaultValue);
          }
        }
      }
    }
  }

}