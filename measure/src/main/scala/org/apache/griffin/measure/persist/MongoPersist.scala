/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.griffin.measure.persist

import org.mongodb.scala._
import org.apache.griffin.measure.utils.ParamUtil._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.Future
import scala.util.{Failure, Success}


case class MongoPersist(config: Map[String, Any], metricName: String, timeStamp: Long) extends Persist {

  MongoConnection.init(config)

  val _MetricName = "metricName"
  val _Timestamp = "timestamp"
  val _Value = "value"

  def available(): Boolean = MongoConnection.dataConf.available

  def start(msg: String): Unit = {}
  def finish(): Unit = {}

  def log(rt: Long, msg: String): Unit = {}

  def persistRecords(df: DataFrame, name: String): Unit = {}
  def persistRecords(records: RDD[String], name: String): Unit = {}
  def persistRecords(records: Iterable[String], name: String): Unit = {}

  def persistMetrics(metrics: Map[String, Any]): Unit = {
    mongoInsert(metrics)
  }

  private val filter = Filters.and(
    Filters.eq(_MetricName, metricName),
    Filters.eq(_Timestamp, timeStamp)
  )

  private def mongoInsert(dataMap: Map[String, Any]): Unit = {
    try {
      val update = Updates.set(_Value, dataMap)
      def func(): (Long, Future[UpdateResult]) = {
        (timeStamp, MongoConnection.getDataCollection.updateOne(
          filter, update, UpdateOptions().upsert(true)).toFuture)
      }
      MongoThreadPool.addTask(func _, 10)
    } catch {
      case e: Throwable => error(e.getMessage)
    }
  }

}

case class MongoConf(url: String, database: String, collection: String) {
  def available: Boolean = url.nonEmpty && database.nonEmpty && collection.nonEmpty
}

object MongoConnection {

  val _MongoHead = "mongodb://"

  val Url = "url"
  val Database = "database"
  val Collection = "collection"

  private var initialed = false

  var dataConf: MongoConf = _
  private var dataCollection: MongoCollection[Document] = _

  def getDataCollection = dataCollection

  def init(config: Map[String, Any]): Unit = {
    if (!initialed) {
      dataConf = mongoConf(config)
      dataCollection = mongoCollection(dataConf)
      initialed = true
    }
  }

  private def mongoConf(cfg: Map[String, Any]): MongoConf = {
    val url = cfg.getString(Url, "").trim
    val mongoUrl = if (url.startsWith(_MongoHead)) url else {
      _MongoHead + url
    }
    MongoConf(
      mongoUrl,
      cfg.getString(Database, ""),
      cfg.getString(Collection, "")
    )
  }
  private def mongoCollection(mongoConf: MongoConf): MongoCollection[Document] = {
    val mongoClient: MongoClient = MongoClient(mongoConf.url)
    val database: MongoDatabase = mongoClient.getDatabase(mongoConf.database)
    database.getCollection(mongoConf.collection)
  }

}