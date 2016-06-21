package oscar.cbls.test.routingS

/*******************************************************************************
  * OscaR is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Lesser General Public License as published by
  * the Free Software Foundation, either version 2.1 of the License, or
  * (at your option) any later version.
  *
  * OscaR is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Lesser General Public License  for more details.
  *
  * You should have received a copy of the GNU Lesser General Public License along with OscaR.
  * If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
  ******************************************************************************/

import oscar.cbls.invariants.core.computation.Store
import oscar.cbls.invariants.core.propagation.ErrorChecker
import oscar.cbls.invariants.lib.seq.Precedence
import oscar.cbls.objective.{CascadingObjective, IntVarObjective}
import oscar.cbls.routing.seq.model._
import oscar.cbls.routing.seq.neighborhood.{OnePointMove, ThreeOpt, TwoOpt1}
import oscar.cbls.search.combinators.{BestSlopeFirst, Profile}


class MySimpleRoutingP(n:Int,v:Int,symmetricDistance:Array[Array[Int]],m:Store, maxPivot:Int ,precedences:List[(Int,Int)])
  extends VRP(n,v,m,maxPivot)
  with TotalConstantDistance with ClosestNeighbors{

  setSymmetricDistanceMatrix(symmetricDistance)

  override protected def getDistance(from : Int, to : Int) : Int = symmetricDistance(from)(to)

  //initializes to something simple; vehicle v-1 does all nodes (but other vehicles)
  setCircuit(nodes)

  val obj = new CascadingObjective(new IntVarObjective(new Precedence(routes,precedences)), totalDistance)
  //val obj = Objective(totalDistance)

  this.addToStringInfo(() => "precedences: " + precedences)
  this.addToStringInfo(() => "objective: " + obj)
  this.addToStringInfo(() => "n:" + n + " v:" + v)

  computeClosestNeighbors()
}

object PrecedenceRouting extends App{

  val n = 100
  val v = 1
  val nbPRecedences = (n - v) / 4

  val maxPivotPerValuePercent = 4

  println("VRPPrecedence(n:" + n + " v:" + v + ")")

  val symmetricDistanceMatrix = RoutingMatrixGenerator(n)._1

  val precedences = RoutingMatrixGenerator.generatePrecedence(n,v,nbPRecedences)


  //  println("restrictions:" + restrictions)
  val model = new Store() //checker = Some(new ErrorChecker()))

  val myVRP = new MySimpleRoutingP(n,v,symmetricDistanceMatrix,model,maxPivotPerValuePercent,precedences)
  val nodes = myVRP.nodes

  model.close()

  println(myVRP)

  val onePtMove = Profile(new OnePointMove(() => nodes, ()=>myVRP.kNearest(40), myVRP))

  val twoOpt = Profile(new TwoOpt1(() => nodes, ()=>myVRP.kNearest(40), myVRP))

  def threeOpt(k:Int, breakSym:Boolean) = Profile(new ThreeOpt(() => nodes, ()=>myVRP.kNearest(k), myVRP,breakSymmetry = breakSym, neighborhoodName = "ThreeOpt(k=" + k + ")"))

  val search = (BestSlopeFirst(List(onePtMove)) exhaust threeOpt(20,true))  afterMove(model.propagate())

  //val search = threeOpt(20,true)
  //search.verboseWithExtraInfo(2, ()=> "" + myVRP)
  search.verbose = 2
  search.paddingLength = 100

  search.doAllMoves(obj=myVRP.obj)

  model.propagate()

  println
  println(myVRP)
  println
  println(search.profilingStatistics)
}
