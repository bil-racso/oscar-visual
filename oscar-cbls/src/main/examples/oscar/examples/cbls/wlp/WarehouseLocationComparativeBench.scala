package oscar.examples.cbls.wlp

import oscar.cbls.invariants.core.computation.{CBLSIntVar, Store}
import oscar.cbls.invariants.lib.logic.Filter
import oscar.cbls.invariants.lib.minmax.MinConstArray
import oscar.cbls.invariants.lib.numeric.Sum
import oscar.cbls.modeling.AlgebraTrait
import oscar.cbls.objective.Objective
import oscar.cbls.search.combinators.LearningRandom
import oscar.cbls.search.{AssignNeighborhood, Benchmark, RandomizeNeighborhood, SwapsNeighborhood}

import scala.language.postfixOps

object WarehouseLocationComparativeBench extends App with AlgebraTrait{

  //the number of warehouses
  val W:Int = 15

  //the number of delivery points
  val D:Int = 150

  println("WarehouseLocation(W:" + W + ", D:" + D + ")")
  //the cost per delivery point if no location is open
  val defaultCostForNoOpenWarehouse = 10000

  val (costForOpeningWarehouse,distanceCost) = WarehouseLocationGenerator.apply(W,D,0,100,3)

  val m = Store()

  val warehouseOpenArray = Array.tabulate(W)(l => CBLSIntVar(m, 0, 0 to 1, "warehouse_" + l + "_open"))
  val openWarehouses = Filter(warehouseOpenArray).setName("openWarehouses")

  val distanceToNearestOpenWarehouse = Array.tabulate(D)(d =>
    MinConstArray(distanceCost(d), openWarehouses, defaultCostForNoOpenWarehouse).setName("distance_for_delivery_" + d))

  val obj = Objective(Sum(distanceToNearestOpenWarehouse) + Sum(costForOpeningWarehouse, openWarehouses))

  m.close()
/*
  val neighborhood = (Statistics(AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse"),true)
    exhaustBack Statistics(SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses"))
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  neighborhood.verbose = 1

  neighborhood.doAllMoves(_>= W + D, obj)

  println(neighborhood.statistics)
*/
  val neighborhood1 = ()=>("exhaustBack",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    exhaustBack SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses")
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val neighborhood2 = ()=>("random",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    random SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses")
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val neighborhood3 = ()=>("LearningRandom",new LearningRandom(List(AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse"),
    SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses")))
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val neighborhood4 = ()=>("onlySwitch",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val neighborhood5 = ()=>("simulatedAnnealing",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    metropolis() maxMoves W/2 withoutImprovementOver obj saveBest obj restoreBestOnExhaust)

  val neighborhood6 = ()=>("roundRobin",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    roundRobin SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses")
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val neighborhood7 = ()=>("best",AssignNeighborhood(warehouseOpenArray, "SwitchWarehouse")
    best SwapsNeighborhood(warehouseOpenArray, "SwapWarehouses")
    orElse (RandomizeNeighborhood(warehouseOpenArray, W/5) maxMoves 2) saveBest obj restoreBestOnExhaust)

  val a = Benchmark.benchtoString(obj,10,neighborhood1,neighborhood2,neighborhood3,neighborhood4,neighborhood5,neighborhood6,neighborhood7)

  println(a)

}