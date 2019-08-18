package com.github.anicolaspp.akka.persistence

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import org.ojai.store.{Connection, DriverManager, QueryCondition, SortOrder}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MapRDBAsyncWriteJournal extends AsyncWriteJournal with ActorLogging with ByteArraySerializer {

  implicit lazy val actorSystem: ActorSystem = context.system
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val config = actorSystem.settings.config

  private val journalPath = config.getString("akka-persistence-maprdb.path")

  def getStoreFor(persistentId: String) = {

    val storePath = journalPath + "/" + persistentId

    if (connection.storeExists(storePath)) {
      connection.getStore(storePath)
    } else {
      connection.createStore(storePath)
    }
  }

  implicit val connection: Connection = DriverManager.getConnection("ojai:mapr:")

  //  private val store = connection.getStore(journalTable)

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    Future.sequence(messages.map(asyncWriteBatch))
  }

  private def asyncWriteBatch(a: AtomicWrite): Future[Try[Unit]] = {
    val batch = Future
      .sequence(a.payload.map(asyncWriteOperation))
      .map(u => Success(u))
      .recover {
        case e => Failure(e)
      }
      .collect {
        case Failure(exception) => Failure(exception)
        case Success(value) => Success({})
      }

    batch
  }

  private def asyncWriteOperation(pr: PersistentRepr): Future[Unit] = {

    toBytes(pr) match {
      case Success(serialized) => Future {
        getStoreFor(pr.persistenceId).insert(Journal.apply(pr.sequenceNr, serialized, pr.deleted))
      }

      case Failure(_) => Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to MapR-DB"))
    }
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
      .is("_id", QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr)
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select("_id")
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

    val messagesToDelete = store.find(query).asScala

    messagesToDelete.foreach { document => store.update(document.getId, connection.newMutation().set("deleted", true)) }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr => Unit): Future[Unit] = Future {

    println(s"from: $fromSequenceNr")
    println(s"to: $toSequenceNr")

    val condition = connection
      .newCondition()
      .and()
      .is("_id", QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toString)
      .is("_id", QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr.toString)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .limit(max)
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

    val messagesToReplay = store.find(query).asScala

    messagesToReplay.foreach { doc =>
      fromBytes[PersistentRepr](doc.getBinary("persistentRepr").array()) match {
        case Success(pr) => recoveryCallback(pr)
        case Failure(_) => Future.failed(throw new RuntimeException("asyncReplayMessages: Failed to deserialize PersistentRepr"))
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    val condition = connection
      .newCondition()
      .is("_id", QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toString)
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .select("_id")
      .orderBy("_id", SortOrder.DESC)
      .limit(1)
      .build()

    import scala.collection.JavaConverters._

    val store = getStoreFor(persistenceId)

    val lowest: Long = store.find(query)
      .asScala
      .headOption
      .map(_.getString("_id").toLong)
      .getOrElse(0)

    println(s"HIGHEST: $lowest")

    lowest
  }
}
