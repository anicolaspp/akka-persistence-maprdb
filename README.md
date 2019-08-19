# akka-persistence-maprdb

[![Build Status](https://travis-ci.com/anicolaspp/akka-persistent-maprdb.svg?branch=master)](https://travis-ci.com/anicolaspp/akka-persistent-maprdb)

This is a plugin for Akka Persistence that uses MapR-DB as backend. It implements a Journal store for saving the corresponding events for persistence entities and a Snapshot store.


### Activation

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

### MapR-DB Configuration

The only configuration key we need from MapR-DB is where our journal and snapshots are going to live. 

The following configuration shows that our journals and snapshots will live inside the folder `/user/mapr/tables/akka` in the MapR File System. We can indicate any valid location in the distributed file system within MFS. 

```
maprdb {
  path = "/user/mapr/tables/akka"
}
```

For each persistence entity a MapR-DB tables will be created. For example, if we have the persistence entity `user` then two tables are automatically created.

```
/user/mapr/tables/akka/user.journal
/user/mapr/tables/akka/user.snapshot
```
These two tables are created automatically the first time the plugin is activated, after that they will consecuentenly be used to read the initial state of the persistence entity when needed and to save new events and snapshots.

### MapR Client

`akka-persistence-maprdb` plugin uses [OJAI](https://mapr.com/docs/61/MapR-DB/JSON_DB/UsingJavaOJAI.html) and the MapR Client to communicate with the MapR Cluster. Make sure you have configured the MapR Client accordingly. In a secured cluster, make sure that the corresponding `mapr ticket` has been created so authentication happens correctly. 

### How is data storey in MapR-DB?

`akka-persistence-maprdb` plugin uses MapR-DB JSON to store the corresponding user defined events and persistence entity snapshots into MapR-DB. 

As mentioned above, each `.journal` table contains the events for the corresponding persistent entity and the following structure is used. 

```json
{
  "_id": {"$binary": <sequenceNr in binary format>},
  "persistentRepr": {"$binary": <persistentRepr in binary format>},
  "deleted": {"$boolean": <true or false>}
}
```

Each row is an even and they are sorted by MapR-DB based on the `_id` in `ASC` order.

Each `.snapshot` table represents the snapshots taken for an especific persistent entity and the following structure is used. 

```json
{
  "_id": "persistenceId_sequenceNr_timestamp", // this is String sorted ASC by default
  "meta": {
    "persistenceId": {"$long": <persistenceId>},
    "sequenceNr": {"$long": <sequenceNr>},
    "timestamp": <timestamp> 
  },
  "snapshot": {"$binary": <binary representation of the Snapshot class>}
}
```
