# akka-persistence-maprdb

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.anicolaspp/akka-persistence-maprdb_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.anicolaspp/akka-persistence-maprdb_2.12) [![Build Status](https://travis-ci.com/anicolaspp/akka-persistence-maprdb.svg?branch=master)](https://travis-ci.com/anicolaspp/akka-persistence-maprdb)

This is a plugin for Akka Persistence that uses MapR-DB as backend. It implements a Journal store for saving the corresponding events for persistence entities and a Snapshot store.

- [Linking](https://github.com/anicolaspp/akka-persistence-maprdb#linking)
- [Plugin Activation](https://github.com/anicolaspp/akka-persistence-maprdb#activation)
- [MapR-DB Configuration](https://github.com/anicolaspp/akka-persistence-maprdb#mapr-db-configuration)
- [Persistence Entity Ids Table](https://github.com/anicolaspp/akka-persistence-maprdb#persistence-entity-ids-table)
- [MapR Client](https://github.com/anicolaspp/akka-persistence-maprdb#mapr-client)
- [How data is stored in MapR-DB](https://github.com/anicolaspp/akka-persistence-maprdb#how-data-is-stored-in-mapr-db)
- [Inspecting your Journals and Snapshots](https://github.com/anicolaspp/akka-persistence-maprdb#inspecting-your-journals-and-snapshots)
- [Journal Tests](https://github.com/anicolaspp/akka-persistence-maprdb#journal-tests)
  - [Test Output](https://github.com/anicolaspp/akka-persistence-maprdb#tests-output)
- [Persistence Query Side](https://github.com/anicolaspp/akka-persistence-maprdb#query-side)
  - [Tagging Events and Events by Tags](https://github.com/anicolaspp/akka-persistence-maprdb#tagging-events-and-events-by-tags)
  - [Querying Tagged Events](https://github.com/anicolaspp/akka-persistence-maprdb#querying-tagged-events)
  - [Ordering and Offsets](https://github.com/anicolaspp/akka-persistence-maprdb#ordering-and-offsets)
  - [MapR-DB Tagged Structure](https://github.com/anicolaspp/akka-persistence-maprdb#mapR-DB-tagged-structure)
- [MapR-DB Performane](https://github.com/anicolaspp/akka-persistence-maprdb#mapr-db-performance)


## Linking

Releases are pushed to Maven Central. Click the Maven Central back to check the latest version there. 

```xml
<dependency>
  <groupId>com.github.anicolaspp</groupId>
  <artifactId>akka-persistence-maprdb_2.12</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

```scala
libraryDependencies += "com.github.anicolaspp" % "akka-persistence-maprdb_2.12" % "X.Y.Z"
```

## Activation

```
akka {
  extensions = [akka.persistence.Persistence]

  # This enables akka-persistence-maprdb plugin
  persistence {
    journal.plugin = "akka-persistence-maprdb.journal"
    snapshot-store.plugin = "akka-persistence-maprdb.snapshot"
  }
}
```

## MapR-DB Configuration

we need some settings to be set up for MapR-DB. 

- `maprdb.path` is the base MFS path where our journals and snapshots live.
- `maprdb.pollingIntervalms` is used by the query side for polling the new persistence entity ids. 
- `maprb.driver.url` can be used to configure what kind of MapR-DB implementation we want to use. `ojai:mapr:` points to the real MapR cluster. However, we could use an in-memory implementation through [ojai-testing](https://github.com/anicolaspp/ojai-testing) by indicating `maprdb.driver.url = ojai:anicolaspp:mem`. Notice that we package `ojai-testing` within `akka-persistence-maprdb` but it should not be used in production.  

The following configuration shows that our journals and snapshots will live inside the folder `/user/mapr/tables/akka` in the MapR File System. We can indicate any valid location in the distributed file system within MFS. The polling interval is `5` seconds and we use a real MapR cluster through the MapR client and driver. For reference about OJAI, please see the related [MapR Documentation](https://mapr.com/docs/61/MapR-DB/JSON_DB/develop-apps-jsonDB.html)

```
maprdb {
  path = "/user/mapr/tables/akka"
  
  pollingIntervalms = 5000
  
  driver {
    url = "ojai:mapr:"
  }
}
```

For each persistence entity, one MapR-DB table is created. For example, if we have the persistence entity `user` then two tables are automatically created.

```
/user/mapr/tables/akka/user.journal
/user/mapr/tables/akka/user.snapshot
```
These two tables are created automatically the first time the plugin is activated, after that they will consequently be used to read the initial state of the persistence entity when needed and to save new events and snapshots.

## Persistence Entity Ids Table

One additional MapR-DB table is created along with your journals and snapshot. The table will have the following name:

```
/user/mapr/tables/akka/ids
```

Notice that the base path is what we indicated in the configuration. The table name is `ids`. This table is set of all `persistence entity ids` that is used in the query side. There are different ways to queries the `persistence entity ids`. One possible way is to obtain a handler to the MapR distributed file system (MFS) and enumerate the tables there. However, having an extra table (`ids`) makes all very easy.

## MapR Client

`akka-persistence-maprdb` plugin uses [OJAI](https://mapr.com/docs/61/MapR-DB/JSON_DB/UsingJavaOJAI.html) and the MapR Client to communicate with the MapR Cluster. Make sure you have configured the MapR Client accordingly. In a secured cluster, make sure that the corresponding `mapr ticket` has been created so authentication happens correctly. 

## How data is stored in MapR-DB?

`akka-persistence-maprdb` plugin uses MapR-DB JSON to store the corresponding user-defined events and persistence entity snapshots into MapR-DB. 

As mentioned above, each `.journal` table contains the events for the corresponding persistent entity and the following structure is used. 

```
{
  "_id": {"$binary": sequenceNr in binary format},
  "persistentRepr": {"$binary": persistentRepr in binary format},
  "deleted": {"$boolean": true or false}
}
```

Each row is an even and they are sorted by MapR-DB based on the `_id` in `ASC` order.

Each `.snapshot` table represents the snapshots taken for a specific persistent entity and the following structure is used. 

```
{
  "_id": "persistenceId_sequenceNr_timestamp", // this is String sorted ASC by default
  "meta": {
    "persistenceId": {"$long": persistenceId},
    "sequenceNr": {"$long": sequenceNr},
    "timestamp": timestamp 
  },
  "snapshot": {"$binary": binary representation of the Snapshot class}
}
```

## Inspecting your Journals and Snapshots

At any moment in time, we could use one of the many available tools to inspect the corresponding structures created by this library. 

### Using MapR DBShell

```sh
mapr dbshell

====================================================
*                  MapR-DB Shell                   *
* NOTE: This is a shell for JSON table operations. *
====================================================
Version: 6.1.0-mapr

MapR-DB Shell

maprdb mapr:> ls /user/mapr/tables/akka/
Found 17 items
tr--------   - mapr 5000          3 2019-08-19 23:46 /user/mapr/tables/akka/ids
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/non-existing-pid.journal
tr--------   - mapr 5000          3 2019-08-19 23:43 /user/mapr/tables/akka/p-1.journal
tr--------   - mapr 5000          3 2019-08-19 23:46 /user/mapr/tables/akka/p-10.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-11.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-12.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-13.journal
tr--------   - mapr 5000          3 2019-08-19 23:46 /user/mapr/tables/akka/p-14.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-15.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-2.journal
tr--------   - mapr 5000          3 2019-08-19 23:37 /user/mapr/tables/akka/p-3.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-4.journal
tr--------   - mapr 5000          3 2019-08-19 23:43 /user/mapr/tables/akka/p-5.journal
tr--------   - mapr 5000          3 2019-08-19 23:46 /user/mapr/tables/akka/p-6.journal
tr--------   - mapr 5000          3 2019-08-19 23:41 /user/mapr/tables/akka/p-7.journal
tr--------   - mapr 5000          3 2019-08-19 23:37 /user/mapr/tables/akka/p-8.journal
```
The previous example shows the `journal` tables and `ids` tables. 

```sh
maprdb mapr:> find /user/mapr/tables/akka/ids

{"_id":"non-existing-pid","path":"/user/mapr/tables/akka/non-existing-pid.journal"}
{"_id":"p-1","path":"/user/mapr/tables/akka/p-1.journal"}
{"_id":"p-10","path":"/user/mapr/tables/akka/p-10.journal"}
{"_id":"p-11","path":"/user/mapr/tables/akka/p-11.journal"}
{"_id":"p-12","path":"/user/mapr/tables/akka/p-12.journal"}
{"_id":"p-13","path":"/user/mapr/tables/akka/p-13.journal"}
{"_id":"p-14","path":"/user/mapr/tables/akka/p-14.journal"}
{"_id":"p-15","path":"/user/mapr/tables/akka/p-15.journal"}
{"_id":"p-2","path":"/user/mapr/tables/akka/p-2.journal"}
{"_id":"p-3","path":"/user/mapr/tables/akka/p-3.journal"}
{"_id":"p-4","path":"/user/mapr/tables/akka/p-4.journal"}
{"_id":"p-5","path":"/user/mapr/tables/akka/p-5.journal"}
{"_id":"p-6","path":"/user/mapr/tables/akka/p-6.journal"}
{"_id":"p-7","path":"/user/mapr/tables/akka/p-7.journal"}
{"_id":"p-8","path":"/user/mapr/tables/akka/p-8.journal"}
{"_id":"p-9","path":"/user/mapr/tables/akka/p-9.journal"}
```
Notice that the `ids` table has each `persistence entity id` along with the path where it lives.

Now let's inspect one of the `journal`s.

```sh
maprdb mapr:> find /user/mapr/tables/akka/p-1.journal

{"_id":{"$binary":"AQ=="},"deleted":false,"persistentRepr":{"$binary":"Cg4IARIKrO0ABXQAA2EtMRABGgNwLTFqJDdjYmUxNmQ4LWY1ZTktNGEyYy04ZjZiLWFlMWNmNWQxNzZkZg=="}}
{"_id":{"$binary":"Ag=="},"deleted":false,"persistentRepr":{"$binary":"Cg4IARIKrO0ABXQAA2EtMhACGgNwLTFqJDdjYmUxNmQ4LWY1ZTktNGEyYy04ZjZiLWFlMWNmNWQxNzZkZg=="}}
{"_id":{"$binary":"Aw=="},"deleted":false,"persistentRepr":{"$binary":"Cg4IARIKrO0ABXQAA2EtMxADGgNwLTFqJDdjYmUxNmQ4LWY1ZTktNGEyYy04ZjZiLWFlMWNmNWQxNzZkZg=="}}
{"_id":{"$binary":"BA=="},"deleted":false,"persistentRepr":{"$binary":"Cg4IARIKrO0ABXQAA2EtNBAEGgNwLTFqJDdjYmUxNmQ4LWY1ZTktNGEyYy04ZjZiLWFlMWNmNWQxNzZkZg=="}}
{"_id":{"$binary":"BQ=="},"deleted":false,"persistentRepr":{"$binary":"Cg4IARIKrO0ABXQAA2EtNRAFGgNwLTFqJDdjYmUxNmQ4LWY1ZTktNGEyYy04ZjZiLWFlMWNmNWQxNzZkZg=="}}
5 document(s) found.
```
The previous example shows the content of `p-1.journal`. Notice that the structure matches to the journal's structure we explained before.

## Journal Tests 

All tests for the journal passed. However, since we don't have a MapR Cluster in Travis we are going to ignore the test. One can run the test locally against a configured MapR Cluster

### Tests Output

```
[info] MyJournalSpec:
[info] A journal
[info] - must replay all messages
[info] - must replay messages using a lower sequence number bound
[info] - must replay messages using an upper sequence number bound
[info] - must replay messages using a count limit
[info] - must replay messages using a lower and upper sequence number bound
[info] - must replay messages using a lower and upper sequence number bound and a count limit
[info] - must replay a single if lower sequence number bound equals upper sequence number bound
[info] - must replay a single message if count limit equals 1
[info] - must not replay messages if count limit equals 0
[info] - must not replay messages if lower  sequence number bound is greater than upper sequence number bound
[info] - must not replay messages if the persistent actor has not yet written messages
[info] - must not replay permanently deleted messages (range deletion)
[info] - must not reset highestSequenceNr after message deletion
[info] - must not reset highestSequenceNr after journal cleanup
[info] A Journal optionally
[info] + CapabilityFlag `supportsRejectingNonSerializableObjects` was turned `off`. To enable the related tests override it with `CapabilityFlag.on` (or `true` in Scala). 
[info] + CapabilityFlag `supportsSerialization` was turned `on`.  
[info] - may serialize events
[info] Run completed in 52 seconds, 904 milliseconds.
[info] Total number of tests run: 15
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 15, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 63 s, completed Aug 19, 2019 2:02:01 AM

```

## Query Side

The current version supports Persistence Read Side. The following queries have been added. 

- `currentPersistenceIds()` gives back the persistence ids in a bounded stream that is closed after completion
- `persistenceIds()` gives back the persistence ids in an unbounded stream that keeps open. New persistence ids will be pushed into this stream.
- `currentEventsByPersistenceId(...)` queries events of the especified `persistence entity id`. 
- `eventsByPersistenceId(...)` is the stream of events of the especified `persistence entity id`. New events will be pushed into this stream as it keeps live. 

```scala
object QueryExample extends App {

  implicit val system = ActorSystem("example")

  implicit val mat = ActorMaterializer()


  val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")

  val events = readJournal.currentEventsByPersistenceId("p-1", 3, Long.MaxValue)
  
  val liveEvents = readJournal.eventsByPersistenceId("p-1", 3, Long.MaxValue)

  val boundedStream = readJournal.currentPersistenceIds().runForeach(println)

  val unboundedStream = readJournal.persistenceIds().runForeach(println)

  Await.result(boundedStream, scala.concurrent.duration.Duration.Inf)
  Await.result(unboundedStream, scala.concurrent.duration.Duration.Inf)
  Await.result(events.runForeach(println), scala.concurrent.duration.Duration.Inf)
  Await.result(liveEvents.runForeach(println), scala.concurrent.duration.Duration.Inf)
}
```

### Tagging Events and Events by Tags

In general, `Tagged` events are just a especial kind of events. All along you could send a `Tagged` event to the 
persistence entity and it ends up being saved in the corresponding `Journal` for the entity. Queries that are based
on events by persistence id will retrieve all the events of the entity (including any `Tagged` event) back to you. 

However, querying `Tagged` events across multiple persistence entities was not possible before version `1.0.6`. 

Since version `1.0.6` we added support for querying `Tagged` events. 

### Crating a tag event

The following `Actor` code shows how we create `Tagged` events. 

```scala
       final case class ExampleState(received: List[String] = Nil) {
           def updated(s: String): ExampleState = copy(s :: received)
       
           override def toString = received.reverse.toString
         }
       
         class ExamplePersistentActor extends PersistentActor {
           override def persistenceId: String = "sample-id-3"
       
           var state = ExampleState()
       
           override def receiveCommand: Receive = {
             case "print" => println("current state = " + state)
             case "snap" => saveSnapshot(state)
             case SaveSnapshotSuccess(metadata) => // ...
             case SaveSnapshotFailure(metadata, reason) => // ...
             case s: String =>
               persist(s) { evt => state = state.updated(evt) }
       
             case (s: String, tags: Seq[String]) =>
               persist(Tagged(s, tags.toSet)) { evt => state = state.updated(evt.payload.toString) }
           }
       
           override def receiveRecover: Receive = {
             case SnapshotOffer(_, s: ExampleState) =>
               println("offered state = " + s)
               state = s
             case evt: String =>
               state = state.updated(evt)
           }
         }
```           

Notice the `case (s: String, tags: Seq[String])` since it will create a `Tagged` event and send it to the `Journal`
to be persisted.  

The following code uses the defined `Actor` to create `Tagged` events.

```scala
val system = ActorSystem("example")
val persistentActor = system.actorOf(Props(classOf[ExamplePersistentActor]), "persistentActor-3-scala")

persistentActor ! ("nick", Seq("boy", "30"))
```

### Querying Tagged Events

The idea of `Tagged` events is that they can be queried across multiple persistence entities. In other words, when
retrieving events with a specific tag, they might not all com from the same persistence entity id, any event in the entire
system that was tagged with the given tag will be retrieved. 

The following code shows how to retrieve `Tagged` events.

```scala    
val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")

val currentEventsByTag = readJournal.currentEventsByTag("boy", Offset.noOffset).runForeach(println)
  
readJournal.eventsByTag("", Offset.sequence(5)).runWith(Sink.asPublisher(publisher))
```     

- `currentEventsByTag` is the sequence of current `Tagged` event by the given tag.
-  `eventsByTag` is an open stream of `Tagged` event by the given tag that is kept alive waiting for more events in live
fashion scenario. 

### Ordering and Offsets

It is important to describe the semantics offered by the library in regard of `ordering` and `offsets` for `Tagged` events.

`Tagged` events are sorted by the `timestamp` at the moment they are written by the `Journal` so they are retrieved in
the exact same order. In highly async contexts, some `Tagged` events might take longer to persist than others. However, the sorting happens using the creation `timestamp` and it does not matter in what order they are actually written to the storage. 

When retrieving `Tagged` events, the only supported `Offset` is `Offset.sequence` any other being passed will be treated
as `Offset.noOffset` meaning that the offset will start at `Offset.sequence(0)`. Since we are retrieving events by tag,
the given offset refers to the offset of the `Tagged` stream after the events for the given tag is calculated.

Let's look at an example that might clarify how it works. 

```scala
taggedEvents: [(t1, e1), (t2, e1), (t1, e2), (t3, e3), (t1, e4), (t1, e5)]
```     

Notice that tag `t1` has events `e1`, `e2`, `e4` and `e5` associated to it. 

Now, if we want the events associated with tag `t1` and `Offset.noOffset`, the events `e1`, `e2`, `e4` and `e5` are 
retrieved since offset was effectively `0`.     

Changing the query by moving the offset will result in a different set of events. Using `Offset.sequence(2)` for instance,
will return only  events `e4` and `e5` since for the given tag `t1` it is skipping the first `2` events. 

It is important to notice that `Offset`s in the events from these queries are stable. In the previous example where `Offset.noOffset`, `e1` will have `Offset` `0` and `e5` will have `Offset` `3`. When `Offset.sequence(2)`, `e4` will have `Offset` `2` and `e5` will have `Offset` `3`. 

In general, the offset is applied to the resultant stream of events associated with the given tag. Notice that the 
given offsets is applied only once, meaning that is your query is `eventsByTag` it will move the cursor the offset, but
all new events after the offset will be pushed in the stream as they arrive.                                                            

### MapR-DB Tagged Structure. 

The internal structure being used for storing `Tagged` events is very simplistic. However, the number of `Tagged` events
can be overwhelming. Even though, MapR-DB is insanely fast for the queries being used, it might be a good idea to 
create a secondary index on `taggedEvents` table. The following command shows how it can be done, but the MapR Control System
can also be used. 

```shell script
maprcli table index add -path /user/mapr/tables/akka/taggedEvents -index tagged_events_idx -indexedfields tags  
```                 

Notice that the based path `/user/mapr/tables/akka/` is where you persistence entities live (configurable) and the 
`taggedEvents` in the table where `Tagged` events are stored.

## MapR-DB Performance

It is known that MapR-DB is quite faster than Cassandra [read paper here](https://mapr.com/blog/database-comparison-an-in-depth-look-at-mapr-db/) while keeping hight consistency with zero tunning. The following [ESG Lab Analysis](https://mapr.com/whitepapers/mike-leone-esg-lab-nosql-benchmark/) shows that MapR-DB yielded an average performance improvement of 2.5x more operations/sec than Cassandra and 5.5x more than HBase.

`akka-persistence-maprdb` plugin has been designed to take advantage of the especific contructs that MapR-DB offers. There is no need to for tunning other than the explained in [MapR-DB Tagged Structure](https://github.com/anicolaspp/akka-persistence-maprdb#mapR-DB-tagged-structure)
