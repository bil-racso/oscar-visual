package oscar.cbls.invariants.tests

import org.scalacheck.Gen
import org.scalatest.prop.Checkers
import oscar.cbls.invariants.lib.logic._
import org.scalatest.FunSuite
import oscar.cbls.invariants.lib.logic.DenseCount
import oscar.cbls.invariants.lib.numeric.Mod
import oscar.cbls.invariants.lib.minmax.Max2
import oscar.cbls.invariants.lib.minmax.MinLin
import oscar.cbls.invariants.lib.set.MakeSet
import oscar.cbls.invariants.lib.minmax.MinSet
import oscar.cbls.invariants.lib.numeric.Sum2
import oscar.cbls.invariants.lib.set.Inter
import oscar.cbls.invariants.lib.set.SetSum
import oscar.cbls.constraints.lib.global.Sequence
import oscar.cbls.invariants.lib.logic.IntITE
import oscar.cbls.invariants.lib.numeric.Prod
import oscar.cbls.invariants.lib.set.Interval
import oscar.cbls.invariants.lib.minmax.MinArray
import oscar.cbls.invariants.lib.set.SetProd
import oscar.cbls.invariants.lib.minmax.ArgMinArray
import oscar.cbls.invariants.lib.minmax.MaxLin
import oscar.cbls.invariants.lib.logic.DenseRef
import oscar.cbls.invariants.lib.numeric.ProdElements
import oscar.cbls.invariants.lib.minmax.Min2
import oscar.cbls.invariants.lib.numeric.Prod2
import oscar.cbls.invariants.lib.numeric.SumElements
import oscar.cbls.invariants.lib.logic.SelectLEHeapHeap
import oscar.cbls.invariants.lib.minmax.ArgMaxArray
import oscar.cbls.invariants.lib.numeric.Sum
import oscar.cbls.invariants.lib.numeric.Minus
import oscar.cbls.constraints.lib.global.MultiKnapsack
import oscar.cbls.invariants.lib.set.Cardinality
import oscar.cbls.constraints.lib.global.AtMost
import oscar.cbls.invariants.lib.numeric.Abs
import oscar.cbls.invariants.lib.minmax.Max
import oscar.cbls.invariants.lib.logic.Filter
import oscar.cbls.invariants.lib.logic.IntElement
import oscar.cbls.invariants.lib.numeric.Step
import oscar.cbls.invariants.lib.logic.IntSetElement
import oscar.cbls.invariants.lib.logic.IntElements
import oscar.cbls.invariants.lib.numeric.Div
import oscar.cbls.invariants.lib.set.Union
import oscar.cbls.invariants.lib.minmax.Min
import oscar.cbls.constraints.lib.global.AllDiff
import oscar.cbls.constraints.lib.global.AtLeast
import oscar.cbls.invariants.lib.set.Diff
import oscar.cbls.invariants.lib.set.TakeAny
import oscar.cbls.invariants.lib.minmax.MaxArray
import oscar.cbls.invariants.lib.minmax.MaxSet
import oscar.cbls.invariants.lib.logic.SelectLESetQueue

class InvariantTests extends FunSuite with Checkers{

  test("AllDiff maintains output = all int vars have different values") {
    val bench = new InvariantTestBench
    new AllDiff(bench.genIntVarsArray(10, -10 to 10))
    bench.run
  }


  test("AtLeast") {
    val bench = new InvariantTestBench
    new AtLeast(bench.genIntVars(10), bench.genBoundedValues(10, 0 to 30, 0 to 10)).toIntVar
    bench.run
  }

  test("AtMost") {
    val bench = new InvariantTestBench
    new AtMost(bench.genIntVars(10), InvGen.randomIntSortedMap(10, 0 to 30, 0 to 30)).toIntVar
    bench.run
  }

  test("MultiKnapsack") {
    val bench = new InvariantTestBench
    //E elements, B bins
    val e:Int = 10
    val b:Int = 5
    new MultiKnapsack(
      bench.genIntVarsArray(e,0 to (b-1)),
      bench.genIntVarsArray(e,0 to 100),
      bench.genIntVarsArray(b,0 to 200)).toIntVar
    bench.run
  }

  test("Sequence") {
    val bench = new InvariantTestBench
    new Sequence(
      bench.genIntVarsArray(20,0 to 10),
      13,
      5,
      (x: Int) => x > 1).toIntVar
    bench.run
  }

  test("Access to ITE maintains output = if ifVar > 0 then thenVar else elseVar") {
    val bench = new InvariantTestBench
    new IntITE(bench.genIntVar(-2 to 3), bench.genIntVar(1 to 2), bench.genIntVar(10 to 11)).toIntVar
    bench.run
  }

  test("Access to int element maintains output = array(index)") {
    val bench = new InvariantTestBench
    new IntElement(bench.genIntVar(0 to 19), bench.genIntVarsArray(20, 0 to 100)).toIntVar
    bench.run
  }

  test("Access to int vars...") {
    val bench = new InvariantTestBench
    new IntElements(bench.genIntSetVar(3, 0 to 4), bench.genIntVarsArray(5, 0 to 10)).toIntSetVar
    bench.run
  }

  test("Access to int set element maintains output = array(index)") {
    val bench = new InvariantTestBench
    new IntSetElement(bench.genIntVar(0 to 19), bench.genIntSetVars(20, 10, 0 to 100)).toIntSetVar
    bench.run
  }

  test("Sparse Cluster maintains a cluster of the indexes of an array.") {
    val bench = new InvariantTestBench
    Cluster.MakeSparse(bench.genIntVarsArray(50),
      (Gen.containerOfN[List, Int](100, Gen.choose(0, 100))).sample.get)
    bench.run
  }

  test("Dense Cluster maintains a cluster of all the indexes of an array.") {
    val bench = new InvariantTestBench
    Cluster.MakeDense(bench.genIntVarsArray(50))
    bench.run
  }

  test("Dense Cluster maintains a cluster of all the indexes of an array"
    + " (assuming min and max).") {
    val bench = new InvariantTestBench
    Cluster.MakeDenseAssumingMinMax(bench.genIntVarsArray(50), 0, 100)
    bench.run
  }

  test("Dense Count maintains count(j) = #{i in index of values | values[i] == j}") {
    val bench = new InvariantTestBench
    new DenseCount(bench.genIntVarsArray(10, 0 to 19), bench.genIntVarsArray(20, 0 to 19, false))
    bench.run
  }

  test("Cross references"){
    val bench = new InvariantTestBench
    DenseRef.makeDenseRef(bench.genIntSetVars(10,10,0 to 20))
    bench.run
  }

  ignore("Cumulative...")(pending)

  test("Filter..."){
    val bench = new InvariantTestBench
    new Filter(bench.genIntVarsArray(4, 0 to 5), (i:Int) => (i % 2) == 0).toIntSetVar
    bench.run
  }

  test("SelectLEHeapHeap") {
    val bench = new InvariantTestBench
    new SelectLEHeapHeap(bench.genIntVarsArray(4, 0 to 5), bench.genIntVar(3 to 10)).toIntSetVar
    bench.run
  }

  ignore("SelectLESetQueue") {
  //TODO exclure les changements de valeurs interdits
    //le pivot ne peut qu'augmenter
    //une valeur en dessous du pivot ne peut que prendre une valeur dépassant toutes les autres valeurs
    //les valeurs au dessus du pivot ne peuvent pas changer
    val bench = new InvariantTestBench
    new SelectLESetQueue(bench.genIntVarsArray(5, 0 to 5), bench.genIntVar(3 to 10, false)).toIntSetVar
    bench.run
  }

  ignore("Predecessor")(pending)

  ignore("Routes")(pending)

  //this is not working so far.
  test("Sort") {
    val bench = new InvariantTestBench
    Sort.MakeSort(bench.genIntVarsArray(4, 0 to 30))
    bench.run
  }

  test("ArgMinArray") {
    val bench = new InvariantTestBench
    new ArgMinArray(bench.genIntVarsArray(20, 0 to 30)).toIntSetVar
    bench.run
  }

  test("ArgMinArray and playing with the index") {
    val bench = new InvariantTestBench
    new ArgMinArray(bench.genIntVarsArray(20, 0 to 30),bench.genIntSetVar(1,0 to 19)).toIntSetVar
    bench.run
  }

  test("ArgMaxArray maintains the set of max variables of the array") {
    val bench = new InvariantTestBench
    new ArgMaxArray(bench.genIntVarsArray(20, 0 to 30)).toIntSetVar
    bench.run
  }

  test("ArgMaxArray and playing with the Index") {
    val bench = new InvariantTestBench
    new ArgMaxArray(bench.genIntVarsArray(20, 0 to 30),bench.genIntSetVar(1,0 to 19)).toIntSetVar
    bench.run
  }

  test("MaxLin") {
    val bench = new InvariantTestBench
    new MaxLin(bench.genSortedIntVars(6, -10 to 10)).toIntVar
    bench.run
  }

  test("MinLin") {
    val bench = new InvariantTestBench
    new MinLin(bench.genSortedIntVars(6, 0 to 10)).toIntVar
    bench.run
  }

  test("Min") {
    val bench = new InvariantTestBench
    new Min(bench.genSortedIntVars(5, -10 to 10)).toIntVar
    bench.run
  }

  test("Max") {
    val bench = new InvariantTestBench
    new Max(bench.genSortedIntVars(5, -10 to 10)).toIntVar
    bench.run
  }

  test("Min2") {
    val bench = new InvariantTestBench
    new Min2(bench.genIntVar(-10 to 10), bench.genIntVar(-10 to 10)).toIntVar
    bench.run
  }

  test("Max2") {
    val bench = new InvariantTestBench
    new Max2(bench.genIntVar(-10 to 10), bench.genIntVar(-10 to 10)).toIntVar
    bench.run
  }

  test("MinArray maintains the minimum from an array of variables.") {
    val bench = new InvariantTestBench
    new MinArray(bench.genIntVarsArray(4, 0 to 100)).toIntVar
    bench.run
  }

  test("MaxArray maintains the maximum from an array of variables.") {
    val bench = new InvariantTestBench
    new MaxArray(bench.genIntVarsArray(2, 0 to 50)).toIntVar
    bench.run
  }

  test("MinSet maintains the minimum of a set.") {
    val bench = new InvariantTestBench
    new MinSet(bench.genIntSetVar()).toIntVar
    bench.run
  }

  test("MaxSet maintains the maximum of a set") {
    val bench = new InvariantTestBench
    new MaxSet(bench.genIntSetVar()).toIntVar
    bench.run
  }

  test("SumElements maintains the sum of variables of which indices are in the given set.") {
    val bench = new InvariantTestBench
    new SumElements(bench.genIntVarsArray(10, 0 to 100), bench.genIntSetVar(5, 0 to 9)).toIntVar
    bench.run
  }

  test("ProdElements maintains the product of variables of which indices are in the given set.") {
    val bench = new InvariantTestBench
    new ProdElements(bench.genIntVarsArray(5, 0 to 10), bench.genIntSetVar(2, 0 to 4)).toIntVar
    bench.run
  }

  test("Sum maintains the sum of input variables.") {
    val bench = new InvariantTestBench
    new Sum(bench.genIntVarsArray(10, 0 to 100)).toIntVar
    bench.run
  }

  test("Prod maintains the product of input variables.") {
    val bench = new InvariantTestBench
    new Prod(bench.genIntVarsArray(3, 0 to 100)).toIntVar
    bench.run
  }

  test("Minus maintains the difference between two variables.") {
    val bench = new InvariantTestBench
    new Minus(bench.genIntVar(0 to 100), bench.genIntVar(0 to 100)).toIntVar
    bench.run
  }

  test("Sum2 maintains the sum of two variables.") {
    val bench = new InvariantTestBench
    new Sum2(bench.genIntVar(0 to 100), bench.genIntVar(0 to 100)).toIntVar
    bench.run
  }

  test("Prod2 maintains the product of two variables") {
    val bench = new InvariantTestBench
    new Prod2(bench.genIntVar(0 to 100), bench.genIntVar(0 to 100)).toIntVar
    bench.run
  }

  test("Div maintains the division of two variables.") {
    val bench = new InvariantTestBench
    new Div(bench.genIntVar(0 to 100),
      bench.genIntVar(1 to 100, true, (v: Int) => v != 0)).toIntVar
    bench.run
  }

  test("Mod maintains the modulo of two variables.") {
    val bench = new InvariantTestBench
    new Mod(bench.genIntVar(0 to 100),
      bench.genIntVar(1 to 100, true, (v: Int) => v != 0)).toIntVar
    bench.run
  }

  test("Abs maintains the absolute value of a variable.") {
    val bench = new InvariantTestBench
    new Abs(bench.genIntVar(-100 to 100)).toIntVar
    bench.run
  }

  test("Step maintains a step function of the input var.") {
    val bench = new InvariantTestBench
    new Step(bench.genIntVar(-100 to 100)).toIntVar
    bench.run
  }

  ignore("RoundUpModulo")(pending)

  ignore("RoundUpCustom")(pending)

  test("Union maintains the union of two sets.") {
    val bench = new InvariantTestBench
    new Union(bench.genIntSetVar(), bench.genIntSetVar()).toIntSetVar
    bench.run
  }

  test("Inter maintains the intersection of two sets.") {
    val bench = new InvariantTestBench
    new Inter(bench.genIntSetVar(), bench.genIntSetVar()).toIntSetVar
    bench.run
  }

  test("Diff maintains the difference between two sets.") {
    val bench = new InvariantTestBench
    new Diff(bench.genIntSetVar(), bench.genIntSetVar()).toIntSetVar
    bench.run
  }

  test("Cardinality maintains the cardinality of a set.") {
    val bench = new InvariantTestBench
    new Cardinality(bench.genIntSetVar()).toIntVar
    bench.run
  }

  test("MakeSet maintains an IntSetVar given a set of IntVar.") {
    val bench = new InvariantTestBench
    new MakeSet(bench.genSortedIntVars(10, 0 to 10)).toIntSetVar
    bench.run
  }

  test("Interval maintains the set in the interval.") {
    val bench = new InvariantTestBench
    new Interval(bench.genIntVar(-100 to 100), bench.genIntVar(-100 to 100)).toIntSetVar
    bench.run
  }

  test("TakeAny maintains a value taken from the set.") {
    val bench = new InvariantTestBench
    new TakeAny(bench.genIntSetVar(), 0).toIntVar
    bench.run
  }

  test("SetSum maintains the sum of variables (after optionnaly appliying a function).") {
    val bench = new InvariantTestBench
    new SetSum(bench.genIntSetVar()).toIntVar
    bench.run
  }

  /**
   * Won't pass when the product products an overflow.
   */
  test("SetProd maintains the product of variables (after optionnaly appliying a function).") {
    val bench = new InvariantTestBench
    new SetProd(bench.genIntSetVar(10, -3 to 3)).toIntVar
    bench.run
  }
}
