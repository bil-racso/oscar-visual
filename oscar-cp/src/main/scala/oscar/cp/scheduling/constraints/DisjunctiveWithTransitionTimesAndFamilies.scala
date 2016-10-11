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
 *******************************************************************************/


package oscar.cp.scheduling.constraints

import oscar.cp.constraints.UnaryResourceWithTransitionTimesAndFamilies
import oscar.cp.core.CPOutcome.{Failure, Success}
import oscar.cp.core.variables.CPIntVar
import oscar.cp.core.{CPOutcome, CPPropagStrength, Constraint}

/**
 * @author Cyrille Dejemeppe (cyrille.dejemeppe@gmail.com)
 * @author Sascha Van Cauwelaert (sascha.vancauwelaert@gmail.com)
 * @author Pierre Schaus (pschaus@gmail.com)
 */
class DisjunctiveWithTransitionTimesAndFamilies(starts: Array[CPIntVar], durations: Array[CPIntVar], ends: Array[CPIntVar], familyMatrix: Array[Array[Int]], families: Array[Int]) extends Constraint(starts(0).store) {

  import oscar.cp.core.CPPropagStrength._

  override def setup(l: CPPropagStrength): CPOutcome = {
    if (starts.nonEmpty) {
      val cp = starts(0).store
      val n = starts.length

      // always add the binary decomposition by default
      for (i <- 0 until n; j <- 0 until n; if i != j) {
        if (cp.post(new BinaryDisjunctiveWithTransitionTimes(starts(i), ends(i), starts(j), ends(j), familyMatrix(i)(j), familyMatrix(j)(i))) == Failure) {
          return Failure
        }
      }
      if (l == Medium || l == Strong) {
        if (cp.post(new UnaryResourceWithTransitionTimesAndFamilies(starts, durations, ends, familyMatrix, families)) == Failure) {
          return Failure
        }
      }

    }
    Success
  }

 

}