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

package org.apache.carbondata.spark.testsuite.datacompaction

import org.apache.spark.sql.test.util.QueryTest

class CheckCsvTest extends QueryTest {


  test("test csv "){
  val df1 =  sqlContext.sparkSession.read.option("inferschema","true").option("header","true").csv("/home/anubhav/Documents/olapActual/Select/UNIQDATA_DEFAULT_TC196.csv")
    val df2 =  sqlContext.sparkSession.read.option("inferschema","true").option("header","true").csv("/home/anubhav/Documents/hiveExpected/Hive/UNIQDATA_DEFAULT_TC196.csv")
    val data1 = df1.collect()
    val data2 = df2.collect()
    println("*********************"+df1.count + "**************" +df2.count)
    checkAnswer(df1,df2)
  }


}
