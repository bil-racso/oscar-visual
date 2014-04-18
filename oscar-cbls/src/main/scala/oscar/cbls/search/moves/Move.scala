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

package oscar.cbls.search.moves

import oscar.cbls.invariants.core.computation.CBLSIntVar

abstract class StatelessNeighborhood extends Neighborhood{
  //this resets the internal state of the move combinators
  override def reset(){}
}

abstract class Move(val objAfter:Int){
  def comit()
}

/** a neighborhood that never finds any move
  */
class NoMove extends StatelessNeighborhood{
  override def getImprovingMove(): Option[Move] = None
}

case class AssingMove(i:CBLSIntVar,v:Int, override val objAfter:Int) extends Move(objAfter){
  override def comit() {i := v}
}

case class SwapMove(i:CBLSIntVar,j:CBLSIntVar, override val objAfter:Int) extends Move(objAfter){
  override def comit() {i :=: j}
}

