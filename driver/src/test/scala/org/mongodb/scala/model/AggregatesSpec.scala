/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.scala.model

import java.lang.reflect.Modifier._

import org.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson

import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Accumulators._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import org.scalatest.{FlatSpec, Matchers}

class AggregatesSpec extends FlatSpec with Matchers {
  val registry = MongoClient.DEFAULT_CODEC_REGISTRY

  def toBson(bson: Bson): Document = Document(bson.toBsonDocument(classOf[BsonDocument], MongoClient.DEFAULT_CODEC_REGISTRY))

  "Aggregates" should "have the same methods as the wrapped Aggregates" in {
    val wrapped = classOf[com.mongodb.client.model.Aggregates].getDeclaredMethods
      .filter(f => isStatic(f.getModifiers) && isPublic(f.getModifiers)).map(_.getName).toSet
    val aliases = Set("filter")
    val local = Aggregates.getClass.getDeclaredMethods.filter(f => isPublic(f.getModifiers)).map(_.getName).toSet -- aliases

    local should equal(wrapped)
  }

  it should "have the same methods as the wrapped Accumulators" in {
    val wrapped = classOf[com.mongodb.client.model.Accumulators].getDeclaredMethods
      .filter(f => isStatic(f.getModifiers) && isPublic(f.getModifiers)).map(_.getName).toSet
    val local = Accumulators.getClass.getDeclaredMethods.filter(f => isPublic(f.getModifiers)).map(_.getName).toSet
    local should equal(wrapped)
  }

  it should "render $addFields" in {
    toBson(addFields(Field("newField", "hello"))) should equal(Document("""{$addFields: { "newField": "hello"}}"""))
  }

  it should "render $bucket" in {
    toBson(bucket("$screenSize", 0, 24, 32, 50, 100000)) should equal(
      Document("""{$bucket: { groupBy: "$screenSize", boundaries: [0, 24, 32, 50, 100000] } } """)
    )
  }

  it should "render $bucketAuto" in {
    toBson(bucketAuto("$price", 4)) should equal(Document("""{ $bucketAuto: { groupBy: "$price", buckets: 4  } }"""))
    toBson(bucketAuto("$price", 4, BucketAutoOptions().granularity(BucketGranularity.R5)
      .output(sum("count", 1), avg("avgPrice", "$price")))) should equal(Document("""{$bucketAuto: {
        groupBy: "$price",
        buckets: 4,
        output: {
        count: {$sum: 1},
        avgPrice: {$avg: "$price"},
      },
        granularity: "R5"
      }
    }"""))
  }

  it should "render $count" in {
    toBson(count()) should equal(Document("""{$count: "count"}"""))
    toBson(count("total")) should equal(Document("""{$count: "total"}"""))
  }

  it should "render $match" in {
    toBson(`match`(Filters.eq("author", "dave"))) should equal(Document("""{ $match : { author : "dave" } }"""))
    toBson(filter(Filters.eq("author", "dave"))) should equal(Document("""{ $match : { author : "dave" } }"""))
  }

  it should "render $facet" in {
    toBson(facet(
      Facet("Screen Sizes", unwind("$attributes"), filter(Filters.equal("attributes.name", "screen size")), group(null, sum("count", 1))),
      Facet("Manufacturer", filter(Filters.equal("attributes.name", "manufacturer")), group("$attributes.value", sum("count", 1)),
        sort(descending("count")), limit(5))
    )) should equal(
      Document("""{$facet: { "Screen Sizes": [{$unwind: "$attributes"}, {$match: {"attributes.name": "screen size"}},
            {$group: { _id: null, count: {$sum: 1} }}],
      "Manufacturer": [ {$match: {"attributes.name": "manufacturer"}}, {$group: {_id: "$attributes.value", count: {$sum: 1}}},
            {$sort: {count: -1}}, {$limit: 5}]}}""")
    )
  }

  it should "render $graphLookup" in {
    toBson(graphLookup("contacts", "$friends", "friends", "name", "socialNetwork")) should equal(
      Document(
        """{ $graphLookup:{ from: "contacts", startWith: "$friends", connectFromField: "friends", connectToField: "name",
          |  as: "socialNetwork" } }""".stripMargin
      )
    )

    toBson(graphLookup("contacts", "$friends", "friends", "name", "socialNetwork", GraphLookupOptions().maxDepth(1))) should equal(
      Document(
        """{ $graphLookup: { from: "contacts", startWith: "$friends", connectFromField: "friends", connectToField: "name",
          |  as: "socialNetwork", maxDepth: 1 } }""".stripMargin
      )
    )

    toBson(graphLookup("contacts", "$friends", "friends", "name", "socialNetwork",
      GraphLookupOptions().maxDepth(1).depthField("master"))) should equal(
      Document(
        """{ $graphLookup: { from: "contacts", startWith: "$friends", connectFromField: "friends", connectToField: "name",
          |  as: "socialNetwork", maxDepth: 1, depthField: "master" } }""".stripMargin
      )
    )

    toBson(graphLookup("contacts", "$friends", "friends", "name", "socialNetwork", GraphLookupOptions()
      .depthField("master"))) should equal(Document(
      """{ $graphLookup: { from: "contacts", startWith: "$friends", connectFromField: "friends",
          |  connectToField: "name", as: "socialNetwork", depthField: "master" } }""".stripMargin
    ))
  }

  it should "render $project" in {
    toBson(project(fields(Projections.include("title", "author"), computed("lastName", "$author.last")))) should equal(
      Document("""{ $project : { title : 1 , author : 1, lastName : "$author.last" } }""")
    )
  }

  it should "render $replaceRoot" in {
    toBson(replaceRoot("$a1")) should equal(Document("""{$replaceRoot: {newRoot: "$a1"}}"""))
  }

  it should "render $sort" in {
    toBson(sort(ascending("title", "author"))) should equal(Document("""{ $sort : { title : 1 , author : 1 } }"""))
  }

  it should "render $sortByCount" in {
    toBson(sortByCount("someField")) should equal(Document("""{ $sortByCount : "someField" }"""))
  }

  it should "render $limit" in {
    toBson(limit(5)) should equal(Document("""{ $limit : 5 }"""))
  }

  it should "render $lookup" in {
    toBson(lookup("from", "localField", "foreignField", "as")) should equal(
      Document("""{ $lookup : { from: "from", localField: "localField", foreignField: "foreignField", as: "as" } }""")
    )
  }

  it should "render $skip" in {
    toBson(skip(5)) should equal(Document("""{ $skip : 5 }"""))
  }

  it should "render $sample" in {
    toBson(sample(5)) should equal(Document("""{ $sample : { size: 5} }"""))
  }

  it should "render $unwind" in {
    toBson(unwind("$sizes")) should equal(Document("""{ $unwind : "$sizes" }"""))
    toBson(unwind("$sizes", UnwindOptions().preserveNullAndEmptyArrays(null))) should equal(
      Document("""{ $unwind : { path : "$sizes" } }""")
    )
    toBson(unwind("$sizes", UnwindOptions().preserveNullAndEmptyArrays(false))) should equal(Document("""
    { $unwind : { path : "$sizes", preserveNullAndEmptyArrays : false } }"""))
    toBson(unwind("$sizes", UnwindOptions().preserveNullAndEmptyArrays(true))) should equal(Document("""
    { $unwind : { path : "$sizes", preserveNullAndEmptyArrays : true } }"""))
    toBson(unwind("$sizes", UnwindOptions().includeArrayIndex(null))) should equal(Document("""{ $unwind : { path : "$sizes" } }"""))
    toBson(unwind("$sizes", UnwindOptions().includeArrayIndex("$a"))) should equal(Document("""
    { $unwind : { path : "$sizes", includeArrayIndex : "$a" } }"""))
    toBson(unwind("$sizes", UnwindOptions().preserveNullAndEmptyArrays(true).includeArrayIndex("$a"))) should equal(Document("""
    { $unwind : { path : "$sizes", preserveNullAndEmptyArrays : true, includeArrayIndex : "$a" } }"""))
  }

  it should "render $out" in {
    toBson(out("authors")) should equal(Document("""{ $out : "authors" }"""))
  }

  it should "render $group" in {
    toBson(group("$customerId")) should equal(Document("""{ $group : { _id : "$customerId" } }"""))
    toBson(group(null)) should equal(Document("""{ $group : { _id : null } }"""))

    toBson(group(Document("""{ month: { $month: "$date" }, day: { $dayOfMonth: "$date" }, year: { $year: "$date" } }"""))) should equal(
      Document("""{ $group : { _id : { month: { $month: "$date" }, day: { $dayOfMonth: "$date" }, year: { $year: "$date" } } } }""")
    )

    val groupDocument = Document("""{
      $group : {
        _id : null,
        sum: { $sum: { $multiply: [ "$price", "$quantity" ] } },
        avg: { $avg: "$quantity" },
        min: { $min: "$quantity" },
        max: { $max: "$quantity" },
        first: { $first: "$quantity" },
        last: { $last: "$quantity" },
        all: { $push: "$quantity" },
        unique: { $addToSet: "$quantity" },
        stdDevPop: { $stdDevPop: "$quantity" },
        stdDevSamp: { $stdDevSamp: "$quantity" }
       }
    }""")

    toBson(group(
      null,
      sum("sum", Document("""{ $multiply: [ "$price", "$quantity" ] }""")),
      avg("avg", "$quantity"),
      min("min", "$quantity"),
      max("max", "$quantity"),
      first("first", "$quantity"),
      last("last", "$quantity"),
      push("all", "$quantity"),
      addToSet("unique", "$quantity"),
      stdDevPop("stdDevPop", "$quantity"),
      stdDevSamp("stdDevSamp", "$quantity")
    )) should equal(groupDocument)
  }

}
