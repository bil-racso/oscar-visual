package oscar.cbls.business.routing.neighborhood.vlsn

import oscar.cbls.Objective
import oscar.cbls.core.search._

import scala.collection.immutable.SortedMap


/*
all neighborhood must return moves that are position-independent.
by default this is not the case. A trait has been added here to ensure that moves are indeed position-independent
 */
class VLSN(v:Int,
           vehicleToRoutedNodesToMove:() => SortedMap[Int,Iterable[Int]],
           unroutedNodesToInsert:() => Iterable[Int],
           nodeToRelevantVehicles:() => Map[Int,Iterable[Int]],

           nodeVehicleToInsertNeighborhood:(Int,Int) => Neighborhood,
           nodeTargetVehicleToMoveNeighborhood:(Int,Int) => Neighborhood,
           nodeToRemoveNeighborhood:Int => Neighborhood,
           removeNodeAndReInsert:Int => () => Unit,

           vehicleToObjective:Array[Objective],
           unroutedPenalty:Objective,
           name:String = "VLSN") extends Neighborhood {

  override def getMove(obj: Objective,
                       initialObj: Int,
                       acceptanceCriterion: (Int, Int) => Boolean): SearchResult = {

    def explore(n:Neighborhood,localObj:Objective):Option[(Move,Int)] = {
      val initialObjective = localObj.value

      //we accept all moves, since degrading moves are allowed in negative cycles
      n.getMove(localObj,initialObjective,acceptanceCriterion = (_,newObj) => newObj != Int.MaxValue) match{
        case NoMoveFound => None
        case MoveFound(m) => Some((m.asInstanceOf[Move],initialObjective - m.objAfter))
      }
    }

    //first, explore the atomic moves, and build VLSN graph
    val vlsnGraph = new MoveExplorerAlgo(
      v:Int,
      vehicleToRoutedNodesToMove(),
      unroutedNodesToInsert(),
      nodeToRelevantVehicles(),

      (node,vehicle) => explore(nodeVehicleToInsertNeighborhood(node,vehicle),vehicleToObjective(vehicle)),
      (node,vehicle) => explore(nodeTargetVehicleToMoveNeighborhood(node,vehicle),vehicleToObjective(vehicle)),
      (node) => explore(nodeToRemoveNeighborhood(node),unroutedPenalty),

      removeNodeAndReInsert,

      initialObj).buildGraph()

    //then, find proper negative cycle in graph
    new CycleFinderAlgoMouthuy(vlsnGraph).findCycle() match{
      case None => NoMoveFound
      case Some(listOfEdge) =>
        //TODO:further explore the graph, to find all independent neg cycles, and improve added value.
        //finally, extract the moves from the graph and return the composite moves
        val moves = listOfEdge.map(edge => edge.move)
        val delta = listOfEdge.map(edge => edge.deltaObj).sum
        MoveFound(CompositeMove(moves,initialObj + delta,name))
    }
  }
}

