package oscar.examples.cbls.routing

import oscar.cbls._
import oscar.cbls.business.routing._
import oscar.cbls.business.routing.invariants.group.GlobalConstraintDefinition
import oscar.cbls.business.routing.invariants.timeWindow.TimeWindowConstraintWithLogReduction
import oscar.cbls.business.routing.model.extensions.TimeWindows
import oscar.cbls.core.search.First
import oscar.cbls.visual.routing.RoutingMapTypes

object VRPTWWithGeoCoords extends App{
  val n = 1000
  val v = 10

  val minLat = 50.404631
  val maxLat = 50.415162
  val minLong = 4.440849
  val maxLong = 4.452595

  new VRPTWWithGeoCoords(n,v,minLat,maxLat,minLong,maxLong)
}

/**
  * Simple example of VRP resolution with OscaR with display on a real map (OSM)
  * @param n The total number of nodes of the problem (with the depots)
  * @param v The total number of vehicles (aka depots)
  * @param minLat The minimum latitude of generated nodes
  * @param maxLat The maximum latitude of generated nodes
  * @param minLong The minimum longitude of generated nodes
  * @param maxLong The maximum longitude of generated nodes
  */
class VRPTWWithGeoCoords (n: Int, v: Int, minLat: Double, maxLat: Double, minLong: Double, maxLong: Double) {
  //////////////////// MODEL ////////////////////
  // The Store : used to store all the model of the problem
  val store = new Store

  // The basic VRP problem, containing the basic needed invariant
  val myVRP = new VRP(store,n,v)
  // Generating the nodes of the problem and making it symmetrical
  val (asymetricDistanceMatrix, geoCoords) = RoutingMatrixGenerator.geographicRandom(n,minLong,maxLong,minLat,maxLat)
  val symmetricDistanceMatrix = Array.tabulate(n)({a =>
    Array.tabulate(n)({b =>
      asymetricDistanceMatrix(a min b)(a max b).toLong
    })})

  // Generating timeMatrix and a time window for each node of the problem
  val timeMatrix = RoutingMatrixGenerator.generateLinearTravelTimeFunction(n,symmetricDistanceMatrix)
  //Strong time windows
  val (strongEarlylines, strongDeadlines, taskDurations, maxWaitingDuration) =
    RoutingMatrixGenerator.generateFeasibleTimeWindows(n,v,timeMatrix)
  val strongTimeWindows = TimeWindows(earliestArrivalTimes = Some(strongEarlylines), latestLeavingTimes = Some(strongDeadlines), taskDurations = taskDurations)
  //Weak time windows
  val weakEarlylines = Array.tabulate(n)(index => if(index < v) strongEarlylines(index) else strongEarlylines(index) + ((strongDeadlines(index)-strongEarlylines(index))/5))
  val weakDeadlines = Array.tabulate(n)(index => if(index < v) strongDeadlines(index) else strongDeadlines(index) - ((strongDeadlines(index)-strongEarlylines(index))/5))
  val weakTimeWindows = TimeWindows(earliestArrivalTimes = Some(strongEarlylines), latestLeavingTimes = Some(weakDeadlines), taskDurations = taskDurations)

  // An invariant that store the total distance travelled by the cars
  val totalDistance = sum(routeLength(myVRP.routes, n, v, false, symmetricDistanceMatrix, true))
  // The STRONG timeWindow constraint (vehicleTimeWindowViolations contains the violation of each vehicle)
  val vehicleTimeWindowViolations = Array.fill(v)(new CBLSIntVar(store,0L,Domain(0L,n)))
  val gc = GlobalConstraintDefinition(myVRP.routes, v)
  val timeWindowStrongConstraint =
    TimeWindowConstraintWithLogReduction(
      gc,
      n,v,
      strongTimeWindows.earliestArrivalTimes,
      strongTimeWindows.latestLeavingTimes,
      strongTimeWindows.taskDurations,
      Array.tabulate(n)(from => Array.tabulate(n)(to => timeMatrix.getTravelDuration(from,0L,to))),
      vehicleTimeWindowViolations
    )

  // The WEAK timeWindow constraint (vehicle can violate the timeWindow constraint but we must minimize this value)
  // If the strong earlyline == weak earlyline ==> no need to define weak earlyline
  // If the strong deadline == weak deadline ==> no need to define weak deadline
  val timeWindowWeakConstraint = forwardCumulativeIntegerIntegerDimensionOnVehicle(
    myVRP.routes,n,v,
    (fromNode,toNode,leaveTimeAtFromNode,totalExcessDurationAtFromNode)=> {
      // Still need to compute the arrival time and leave time based on STRONG time windows
      val arrivalTimeAtToNode = leaveTimeAtFromNode + timeMatrix.getTravelDuration(fromNode,0,toNode)
      val leaveTimeAtToNode =
        if(toNode < v) 0
        else Math.max(arrivalTimeAtToNode,strongEarlylines(toNode)) + taskDurations(toNode)

      val totalExcessDurationAtToNode = totalExcessDurationAtFromNode +
        Math.max(0,leaveTimeAtToNode-weakDeadlines(toNode)) +           // If weakDeadline < leaveTimeAtTo < strongDeadline
      Math.max(0, weakEarlylines(toNode)-arrivalTimeAtToNode)           // If strongEarlyline < arrivalTimeAtTo < weakEarlyline
      (leaveTimeAtToNode,totalExcessDurationAtToNode)
    },
    Array.tabulate(v)(x => new CBLSIntConst(strongEarlylines(x)+taskDurations(x))),
    Array.tabulate(v)(x => new CBLSIntConst(0)),
    0,
    0,
    contentName = "Time excess at node"
  )
  val totalExcessTimeForWeakConstraint = sum(timeWindowWeakConstraint.content2AtEnd)

  // A penalty given to all unrouted nodes to force the optimisation to route them
  val unroutedPenalty = 1000000
  // The objectif function :
  // If there is no time window violation :
  //    unroutedNode*penalty + totalDistance ==> To minimize
  val obj = new CascadingObjective(sum(vehicleTimeWindowViolations),
    (n-length(myVRP.routes))*unroutedPenalty + totalDistance + totalExcessTimeForWeakConstraint)

  store.close()


  //////////////////// Pruning and display ////////////////////
  ////////// Display VRP resolution on real map //////////

  val routingDisplay = display(myVRP,geoCoords,routingMapType = RoutingMapTypes.RealRoutingMap, refreshRate = 10)

  ////////// Static Pruning (done once before starting the resolution) //////////

  // Relevant predecessors definition for each node (here any node can be the precessor of another node)
  val relevantPredecessorsOfNodes = TimeWindowHelper.relevantPredecessorsOfNodes(myVRP, weakTimeWindows, timeMatrix)
  // Sort them lazily by distance
  val closestRelevantNeighborsByDistance =
    Array.tabulate(n)(DistanceHelper.lazyClosestPredecessorsOfNode(symmetricDistanceMatrix,relevantPredecessorsOfNodes)(_))

  ////////// Dynamic pruning (done each time before evaluation a move) //////////
  // Only condition the new neighbor must be routed
  val routedPostFilter = (node:Long) => (neighbor:Long) => myVRP.isRouted(neighbor)

  //////////////////// Search Procedure ////////////////////
  ////////// Neighborhood definition //////////

  // Takes an unrouted node and insert it at the best position within the 10 closest nodes (inserting it after this node)
  val routeUnroutedPoint =  profile(insertPointUnroutedFirst(myVRP.unrouted,
    ()=>myVRP.kFirst(20,closestRelevantNeighborsByDistance(_),routedPostFilter),
    myVRP,
    neighborhoodName = "InsertUF",
    hotRestart = false,
    selectNodeBehavior = First(), // Select the first unrouted node in myVRP.unrouted
    selectInsertionPointBehavior = First())) // Inserting after the first node in myVRP.kFirst(10,...)

  // Moves a routed node to a better place (best neighbor within the 10 closest nodes)
  def onePtMove(k:Long) = profile(onePointMove(
    myVRP.routed,
    () => myVRP.kFirst(k,closestRelevantNeighborsByDistance(_),routedPostFilter),
    myVRP))

  ////////// Final search procedure //////////

  // bestSlopeFirst => Perform the best neighborhood in the list (meaning the one that reduces the most the objective function)
  // afterMove => after each move update the routing display
  val searchProcedure =
    routeUnroutedPoint.
      exhaust(onePtMove(20)).
      afterMove(routingDisplay.drawRoutes())


  //////////////////// RUN ////////////////////

  searchProcedure.verbose = 1
  searchProcedure.doAllMoves(obj = obj)
  routingDisplay.drawRoutes(true)
  println(myVRP)
  println(searchProcedure.profilingStatistics)
}