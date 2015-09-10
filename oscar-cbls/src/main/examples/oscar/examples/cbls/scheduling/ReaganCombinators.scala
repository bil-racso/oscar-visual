/*******************************************************************************
 * This file is part of OscaR (Scala in OR).
 *
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/gpl-3.0.html
 ******************************************************************************/

/*******************************************************************************
 * Contributors:
 *     This code has been initially developed by CETIC www.cetic.be
 *         by Renaud De Landtsheer
 ******************************************************************************/

package oscar.examples.cbls.scheduling

import oscar.cbls.invariants.core.computation.{CBLSIntVar, Store}
import oscar.cbls.objective.Objective
import oscar.cbls.scheduling._
import oscar.cbls.scheduling.model._
import oscar.cbls.invariants.core.propagation.Checker
import oscar.cbls.scheduling.model.CumulativeResource
import oscar.cbls.scheduling.solver.{SchedulingStrategies, IFlatIRelax}
import oscar.cbls.search.combinators.BasicSaveBest

/**a simple model of Reagan president of USA
 * he is partly multitask, can do two things at the same time, except eating, which requires his full attention
 * he needs to sleep 2H, eat 30', chew 45' and think 3H
 * he cannot sleep before having eaten
 */
object ReaganCombinators extends App {
  val model = new Store(verbose=false, checker = None, noCycle=false, topologicalSort = true)

  val planning = new Planning(model, 50)

//  implicit def SymToString(s:Symbol):String = s.name

  val Reagan = CumulativeResource(planning, 3, "Reagan")

  val Eat = Activity(2, planning, "eat")
  Eat uses 2 ofResource Reagan

  val Sleep = Activity(8, planning, "sleep")
  Sleep uses 1 ofResource Reagan

  val Think = Activity(12, planning, "think")
  Think uses 1 ofResource Reagan

  val Chew = Activity(3, planning, "chew")
  Chew uses 1 ofResource Reagan

  val Speak = Activity(3, planning, "speak")
  Speak uses 3 ofResource Reagan

  val Drink = Activity(3, planning, "drink")
  Drink uses 3 ofResource Reagan

  val Pray = NonMoveableActivity(5, 2, planning, "pray")
  Pray uses 2 ofResource Reagan

  val Digest = SuperActivity(Eat, Sleep, "digest")
  Digest uses 1 ofResource Reagan

  Think precedes Drink
  Eat precedes Sleep
  Chew precedes Speak

  val makeSpan:Objective = planning.makeSpan
  model.close(false)

  val iFlatRelax = SchedulingStrategies.iFlatRelax(planning, makeSpan, nbRelax= 4,
    pKillPerRelax = 50, stable = 3)

  iFlatRelax.verbose = 2
  iFlatRelax.doAllMoves(_ > 100, null)

  //val solver = new IFlatIRelax(planning)

  //solver.solve(maxIt = 20, stable = 10)

  println(planning.toAsciiArt)
  println(planning.resourceUsage)
  println(planning.dependencies)

}
