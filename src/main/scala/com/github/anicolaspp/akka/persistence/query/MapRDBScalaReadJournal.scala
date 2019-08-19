package com.github.anicolaspp.akka.persistence.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.scaladsl.{CurrentPersistenceIdsQuery, ReadJournal}
import akka.stream.scaladsl.Source
import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.ojai.StorePool
import com.github.anicolaspp.akka.persistence.{MapRDB, MapRDBConnectionProvider}
import com.typesafe.config.Config
import org.ojai.store.{Connection, DocumentStore}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class MapRDBScalaReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with MapRDBConnectionProvider {

  /**
   * Same type of query as [[akka.persistence.query.scaladsl.PersistenceIdsQuery#persistenceIds]] but the stream
   * is completed immediately when it reaches the end of the "result set". Persistent
   * actors that are created after the query is completed are not included in the stream.
   */
  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromGraph(new CurrentPersistenceIdsSource(StorePool.idsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY))))

  override def actorSystemConfiguration: Config = system.settings.config
}

object MapRDBScalaReadJournal {
  def apply(system: ExtendedActorSystem, config: Config): MapRDBScalaReadJournal = new MapRDBScalaReadJournal(system: ExtendedActorSystem, config: Config)
}

class CurrentPersistenceIdsSource(store: DocumentStore)(implicit connection: Connection)
  extends GraphStage[SourceShape[String]] {

  private val out: Outlet[String] = Outlet("CurrentPersistenceIdsSource")

  override def shape: SourceShape[String] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogicWithLogging(shape) {
    private var start = true
    private var index = 0
    private var buffer = mutable.Queue.empty[String]

    private implicit def ec: ExecutionContextExecutor = materializer.executionContext

    //    private val StringSeq = classTag[Seq[String]]

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (buffer.isEmpty && (start || index > 0)) {
          val callback = getAsyncCallback[Seq[String]] { docs =>

            start = false
            buffer.enqueue(docs: _*)

            deliver()
          }

          tryQuery(store).fold(
            e => getAsyncCallback[Unit] { _ => failStage(e) }.invoke(()),
            value => callback.invoke(value))

        } else {
          deliver()
        }
      }
    }

    )

    private def tryQuery(store: DocumentStore) = Try {
      import scala.collection.JavaConverters._

      store.find().asScala.map(_.getIdString).toSeq
    }

    private def deliver(): Unit = {
      if (buffer.nonEmpty) {
        val elem = buffer.dequeue
        push(out, elem)
      } else {
        // we're done here, goodbye
        completeStage()
      }
    }
  }
}

object CurrentPersistenceIdsSource {

  case object Continue

}