package com.github.anicolaspp.akka.persistence.journal

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence._
import akka.persistence.journal.AsyncWriteJournal
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.MapRDB._
import com.github.anicolaspp.akka.persistence.ojai.StorePool
import org.ojai.store._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MapRDBJournal extends AsyncWriteJournal
  with ActorLogging
  with ByteArraySerializer {

  implicit lazy val actorSystem: ActorSystem = context.system

  implicit val connection: Connection = DriverManager.getConnection(MAPR_CONFIGURATION_STRING)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val config = actorSystem.settings.config

  private val journalPath = config.getString(PATH_CONFIGURATION_KEY)

  private val storesPool = StorePool.journalFor(journalPath)

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

  private def asyncWriteBatch(a: AtomicWrite): Future[Try[Unit]] = Future
    .sequence(a.payload.map(asyncWriteOperation))
    .map(u => Success(u))
    .recover {
      case e => Failure(e)
    }
    .collect {
      case Failure(exception) => Failure(exception)
      case Success(_) => Success({})
    }

  private def asyncWriteOperation(pr: PersistentRepr): Future[Unit] = toBytes(pr) match {
    case Success(serialized) => Future {
      storesPool.getStoreFor(pr.persistenceId).insert(Journal.toMapRDBRow(pr.sequenceNr, serialized, pr.deleted))
    }

    case Failure(_) => Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to MapR-DB"))
  }
}