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

package org.apache.spark.sql.execution.datasources.v2

import scala.jdk.CollectionConverters.MapHasAsJava

import org.apache.spark.internal.LogKey.NAMESPACE
import org.apache.spark.internal.MDC
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.analysis.NamespaceAlreadyExistsException
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.connector.catalog.SupportsNamespaces
import org.apache.spark.sql.errors.QueryCompilationErrors
import org.apache.spark.util.Utils

/**
 * Physical plan node for creating a namespace.
 */
case class CreateNamespaceExec(
    catalog: SupportsNamespaces,
    namespace: Seq[String],
    ifNotExists: Boolean,
    private var properties: Map[String, String])
    extends LeafV2CommandExec {
  override protected def run(): Seq[InternalRow] = {
    import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._
    import org.apache.spark.sql.connector.catalog.SupportsNamespaces._

    val ns = namespace.toArray
    if (!catalog.namespaceExists(ns)) {
      try {
        val ownership =
          Map(PROP_OWNER -> Utils.getCurrentUserName())
        catalog.createNamespace(ns, (properties ++ ownership).asJava)
      } catch {
        case _: NamespaceAlreadyExistsException if ifNotExists =>
          logWarning(log"Namespace ${MDC(NAMESPACE, namespace.quoted)} was created concurrently. " +
            log"Ignoring.")
      }
    } else if (!ifNotExists) {
      throw QueryCompilationErrors.namespaceAlreadyExistsError(ns)
    }

    Seq.empty
  }

  override def output: Seq[Attribute] = Seq.empty
}
