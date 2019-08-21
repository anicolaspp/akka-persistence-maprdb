package com.github.anicolaspp.akka.persistence.journal

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence._
import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.MapRDB._
import com.github.anicolaspp.akka.persistence.ojai.MapRDBConnectionProvider
import com.github.anicolaspp.akka.persistence.ojai.stores.StorePool
import com.typesafe.config.Config
import org.ojai.store._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MapRDBJournal extends AsyncWriteJournal
  with ActorLogging
  with ByteArraySerializer
  with MapRDBConnectionProvider {

  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val journalPath = actorSystemConfiguration.getString(PATH_CONFIGURATION_KEY)

  private val storesPool = StorePool.journalFor(journalPath)

  override implicit lazy val actorSystem: ActorSystem = context.system

  override def actorSystemConfiguration: Config = actorSystem.settings.config

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
    Future.sequence(messages.map(asyncWriteBatch))

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr.toBinaryId())
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select(MAPR_ENTITY_ID)
      .build()

    import scala.collection.JavaConverters._

    val store = storesPool.getStoreFor(persistenceId)

    val messagesToDelete = store.find(query).asScala

    messagesToDelete.foreach { document => store.update(document.getId, connection.newMutation().set(MAPR_DELETED_MARK, true)) }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr => Unit): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .and()
      .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toBinaryId())
      .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr.toBinaryId())
      .is(MAPR_DELETED_MARK, QueryCondition.Op.EQUAL, false)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .limit(max)
      .build()

    import scala.collection.JavaConverters._

    val store = storesPool.getStoreFor(persistenceId)

    store.find(query).asScala.foreach { doc =>
      fromBytes[PersistentRepr](Journal.getBinaryRepresentationFrom(doc)) match {
        case Success(pr) => recoveryCallback(pr)
        case Failure(_) => Future.failed(throw new RuntimeException("asyncReplayMessages: Failed to deserialize PersistentRepr"))
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    val condition = connection
      .newCondition()
      .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toBinaryId())
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select(MAPR_ENTITY_ID)
      .orderBy(MAPR_ENTITY_ID, SortOrder.DESC)
      .limit(1)
      .build()

    val it = storesPool.getStoreFor(persistenceId).find(query).iterator()

    val highest = if (it.hasNext) {
      BigInt(it.next().getBinary(MAPR_ENTITY_ID).array()).toLong
    } else {
      0
    }

    highest
  }

  private def asyncWriteBatch(a: AtomicWrite): Future[Try[Unit]] =
    Future.sequence(a.payload.map(asyncWriteOperation))
      .map[Try[Unit]](u => Success(u))
      .recover {
        case ex => Failure(ex)
      }

  private def asyncWriteOperation(pr: PersistentRepr): Future[Unit] = toBytes(pr) match {
    case Success(serialized) => Future {
      storesPool
        .getStoreFor(pr.persistenceId)
        .insert(Journal.toMapRDBRow(pr.persistenceId, pr.sequenceNr, serialized, pr.deleted))

      getTags(pr).map(tagged => writeTags(tagged, serialized))
    }

    case Failure(_) => Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to MapR-DB"))
  }

  private def getTags(pr: PersistentRepr): Option[Tagged] = pr.payload match {
    case tagged@Tagged(_, _) => Some(tagged)
    case _ => None
  }

  private def writeTags(tagged: Tagged, eventSerializedRepresentation: Array[Byte]): Unit =
    tagged
      .tags
      .map(tag => Journal.tagToMapRDBRow(tag, eventSerializedRepresentation))
      .foreach(storesPool.getTagsStore().insert)

}

