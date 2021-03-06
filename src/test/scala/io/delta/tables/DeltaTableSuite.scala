/*
 * Copyright (2020) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.tables

import java.util.Locale

// scalastyle:off import.ordering.noEmptyLine
import org.apache.spark.sql.delta.test.DeltaSQLCommandTest

import org.apache.spark.sql.{AnalysisException, QueryTest}
import org.apache.spark.sql.test.SharedSparkSession

class DeltaTableSuite extends QueryTest
  with SharedSparkSession
  with DeltaSQLCommandTest {

  test("forPath") {
    withTempDir { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      checkAnswer(
        DeltaTable.forPath(spark, dir.getAbsolutePath).toDF,
        testData.collect().toSeq)
      checkAnswer(
        DeltaTable.forPath(dir.getAbsolutePath).toDF,
        testData.collect().toSeq)
    }
  }

  test("forPath - with non-Delta table path") {
    val msg = "not a delta table"
    withTempDir { dir =>
      testData.write.format("parquet").mode("overwrite").save(dir.getAbsolutePath)
      testError(msg) { DeltaTable.forPath(spark, dir.getAbsolutePath) }
      testError(msg) { DeltaTable.forPath(dir.getAbsolutePath) }
    }
  }

  test("forName") {
    withTempDir { dir =>
      withTable("deltaTable") {
        testData.write.format("delta").saveAsTable("deltaTable")

        checkAnswer(
          DeltaTable.forName(spark, "deltaTable").toDF,
          testData.collect().toSeq)
        checkAnswer(
          DeltaTable.forName("deltaTable").toDF,
          testData.collect().toSeq)

      }
    }
  }

  def testForNameOnNonDeltaName(tableName: String): Unit = {
    val msg = "not a Delta table"
    testError(msg) { DeltaTable.forName(spark, tableName) }
    testError(msg) { DeltaTable.forName(tableName) }
  }

  test("forName - with non-Delta table name") {
    withTempDir { dir =>
      withTable("notADeltaTable") {
        testData.write.format("parquet").mode("overwrite").saveAsTable("notADeltaTable")
        testForNameOnNonDeltaName("notADeltaTable")
      }
    }
  }

  test("forName - with temp view name") {
    withTempDir { dir =>
      withTempView("viewOnDeltaTable") {
        testData.write.format("delta").save(dir.getAbsolutePath)
        spark.read.format("delta").load(dir.getAbsolutePath)
          .createOrReplaceTempView("viewOnDeltaTable")
        testForNameOnNonDeltaName("viewOnDeltaTable")
      }
    }
  }

  test("forName - with delta.`path`") {
    withTempDir { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      testForNameOnNonDeltaName(s"delta.`$dir`")
    }
  }

  test("as") {
    withTempDir { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      checkAnswer(
        DeltaTable.forPath(dir.getAbsolutePath).as("tbl").toDF.select("tbl.value"),
        testData.select("value").collect().toSeq)
    }
  }

  test("isDeltaTable - path") {
    withTempDir { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      assert(DeltaTable.isDeltaTable(dir.getAbsolutePath))
    }
  }

  test("isDeltaTable - with non-Delta table path") {
    withTempDir { dir =>
      testData.write.format("parquet").mode("overwrite").save(dir.getAbsolutePath)
      assert(!DeltaTable.isDeltaTable(dir.getAbsolutePath))
    }
  }

  test("toDF regenerated each time") {
    withTempDir { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      val table = DeltaTable.forPath(dir.getAbsolutePath)
      assert(table.toDF != table.toDF)
    }
  }

  def testError(expectedMsg: String)(thunk: => Unit): Unit = {
    val e = intercept[AnalysisException] { thunk }
    assert(e.getMessage.toLowerCase(Locale.ROOT).contains(expectedMsg.toLowerCase(Locale.ROOT)))
  }
}
