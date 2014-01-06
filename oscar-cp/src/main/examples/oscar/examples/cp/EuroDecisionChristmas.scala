package oscar.examples.cp

import oscar.cp.modeling._
import oscar.cp.core._
import oscar.visual._
import java.awt.Color

/**
 * Try to maximize the number of children receiving a gifts given the number of gifts they asked
 * and the capacity of Santa (28 gifts).
 *
 * @author Pierre Schaus pschaus@gmail.com
 * @author Renaud Hartert ren.hartert@gmail.com
 */
object EuroDecisionChistmas extends CPModel with App {

  val names = Array("Fulkerson", "Dijkstra", "Benders", "Dantzig", "Konig")
  val weights = Array(10, 8, 5, 5, 15) // number of gifts
  val profit = Array(2, 3, 0, 1, 5) // number of children

  val x = Array.fill(names.size)(CPVarBool())
  val obj = CPVarInt(0 to profit.sum)
  val capacity = CPVarInt(0 to 28)

  add(binaryKnapsack(x, profit, weights, obj, capacity))

  maximize(obj) search { binaryStatic(x) }

  onSolution {
    println("objective:" + obj.value)
    for (i <- 0 until names.size; if (x(i).value == 1)) {
      println(names(i))
    }
  }

  val stats = start()

  println(stats)
}
