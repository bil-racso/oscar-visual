/**
 * *****************************************************************************
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
 * ****************************************************************************
 */
/**
 * *****************************************************************************
 * Contributors:
 *     This code has been initially developed by Ghilain Florent.
 *     Refactored with respect to the new architecture by Yoann Guyot.
 * ****************************************************************************
 */

package oscar.cbls.routing.neighborhood

import oscar.cbls.routing.model._
import oscar.cbls.search.algo.{IdenticalAggregator, HotRestart}

/**
 * Inserts an unrouted point in a route.
 * The search complexity is O(n²).
 *
 * PRE-CONDITIONS:
 * - the relevant neighbors must all be routed,
 * - the primary node iterator must contain only unrouted nodes.
 * @param nodeSymmetryClass a function that input the ID of an unrouted node and returns a symmetry class;
 *                      ony one of the unrouted node in each class will be considered for insert
 *                      Int.MinValue is considered different to itself
 *                      if you set to None this will not be used at all
 * @author renaud.delandtsheer@cetic.be
 * @author Florent Ghilain (UMONS)
 * @author yoann.guyot@cetic.be
 */
class InsertPointSym(unroutedNodesToInsert: () => Iterable[Int],
                       relevantNeighbors: () => Int => Iterable[Int],
                       nodeSymmetryClass:Option[Int => Int] = None,
                       vrp: VRP,
                       neighborhoodName: String = null,
                       best: Boolean = false,
                       hotRestart: Boolean = true) extends InsertPoint(unroutedNodesToInsert, relevantNeighbors, vrp, neighborhoodName, best, hotRestart){
  //the indice to start with for the exploration

  override def exploreNeighborhood(): Unit = {

    val iterationSchemeOnZone =
      if (hotRestart && !best) HotRestart(unroutedNodesToInsert(), startIndice)
      else unroutedNodesToInsert()

    val iterationScheme = nodeSymmetryClass match {
      case None => iterationSchemeOnZone
      case Some(s) => IdenticalAggregator.removeIdenticalClassesLazily(iterationSchemeOnZone, s)
    }
    cleanRecordedMoves()
    val relevantNeighborsNow = relevantNeighbors()

    for (insertedPoint <- iterationScheme) {
      assert(!vrp.isRouted(insertedPoint),
        "The search zone should be restricted to unrouted nodes when inserting.")

      for (
        beforeInsertedPoint <- relevantNeighborsNow(insertedPoint) if vrp.isRouted(beforeInsertedPoint)
      ) {
        assert(isRecording, "MoveDescription should be recording now")

        encode(beforeInsertedPoint, insertedPoint)
        val newObj = evalObjOnEncodedMove()

        if (moveRequested(newObj)
          && submitFoundMove(InsertPointMove(beforeInsertedPoint, insertedPoint, newObj, this, neighborhoodName))) {
          startIndice = insertedPoint + 1
          return
        }
      }
    }
  }
}
