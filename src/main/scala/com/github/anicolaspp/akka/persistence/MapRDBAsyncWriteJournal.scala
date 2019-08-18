package com.github.anicolaspp.akka.persistence

import java.nio.ByteBuffer

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import com.github.anicolaspp.akka.persistence.MapRDBAsyncWriteJournal._
import org.ojai.store.{Connection, DocumentStore, DriverManager, QueryCondition, SortOrder}
import org.ojai.util.Documents

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MapRDBAsyncWriteJournal extends AsyncWriteJournal with ActorLogging with ByteArraySerializer {

  implicit lazy val actorSystem: ActorSystem = context.system

  implicit val connection: Connection = DriverManager.getConnection(MAPR_CONFIGURATION_STRING)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val config = actorSystem.settings.config

  private val journalPath = config.getString(PATH_CONFIGURATION_KEY)

  private var stores = Map.empty[String, DocumentStore]

  private def getStoreFor(persistentId: String): DocumentStore = {

    val storePath = journalPath + "/" + persistentId

    if (stores.contains(storePath)) {
      stores(storePath)
    } else {

      val store = if (connection.storeExists(storePath)) {
        connection.getStore(storePath)
      } else {
        connection.createStore(storePath)
      }

      stores = stores + (storePath -> store)

      store
    }
  }

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = Future.sequence(messages.map(asyncWriteBatch))

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
      getStoreFor(pr.persistenceId).insert(Journal.toMapRDBRow(pr.sequenceNr, serialized, pr.deleted))
    }

    case Failure(_) => Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to MapR-DB"))
  }

  /**
   * Plugin API: asynchronously deletes all persistent messages up to `toSequenceNr`
   * (inclusive).
   *
   * This call is protected with a circuit-breaker.
   * Message deletion doesn't affect the highest sequence number of messages,
   * journal must maintain the highest sequence number and never decrease it.
   */
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, ByteBuffer.wrap(BigInt(toSequenceNr).toByteArray))
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select(MAPR_ENTITY_ID)
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

    val messagesToDelete = store.find(query).asScala

    messagesToDelete.foreach { document => store.update(document.getId, connection.newMutation().set(MAPR_DELETED_MARK, true)) }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr => Unit): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .and()
      .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, ByteBuffer.wrap(BigInt(fromSequenceNr).toByteArray))
      .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, ByteBuffer.wrap(BigInt(toSequenceNr).toByteArray))
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .limit(max)
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

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
      .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, ByteBuffer.wrap(BigInt(fromSequenceNr).toByteArray))
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select(MAPR_ENTITY_ID)
      .orderBy(MAPR_ENTITY_ID, SortOrder.DESC)
      .limit(1)
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

    //    println(query.asJsonString())

    val it = store.find(query).iterator()

    val highest = if (it.hasNext) {
      BigInt(it.next().getBinary(MAPR_ENTITY_ID).array()).toLong
    } else {
      0
    }
    println(s"HIGHEST: $highest")
    highest
  }
}

object MapRDBAsyncWriteJournal {
  lazy val PATH_CONFIGURATION_KEY = "akka-persistence-maprdb.path"

  lazy val MAPR_CONFIGURATION_STRING = "ojai:mapr:"

  lazy val MAPR_ENTITY_ID = "_id"

  lazy val MAPR_DELETED_MARK = "deleted"

  lazy val MAPR_BINARY_MARK = "persistentRepr"
}