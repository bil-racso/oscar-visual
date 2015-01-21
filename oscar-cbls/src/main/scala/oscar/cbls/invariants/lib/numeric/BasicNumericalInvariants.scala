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
/*******************************************************************************
  * Contributors:
  *     This code has been initially developed by CETIC www.cetic.be
  *         by Renaud De Landtsheer
  *            Yoann Guyot
  ******************************************************************************/

package oscar.cbls.invariants.lib.numeric

import oscar.cbls.invariants.core.computation._
import oscar.cbls.invariants.core.propagation.Checker
import oscar.cbls.invariants.lib.logic._;

object Sum{
  def apply(vars: Array[IntValue], cond: SetValue) = SumElements(vars, cond)
  def apply(vars: Iterable[IntValue]) = new Sum(vars)
  def apply(vars:Array[Int], cond:SetValue) = SumConstants(vars,cond)
}


object Prod{
  def apply(vars: Iterable[IntValue]) = new Prod(vars)
  def apply(vars: Array[IntValue], cond: SetValue) = ProdElements(vars, cond)
  def apply(vars: Array[Int], cond: SetValue) = ProdConstants(vars, cond)
}

/**
 * sum(vars)
 * @param vars is an iterable of IntVars
 * @author renaud.delandtsheer@cetic.be
 * */
class Sum(vars: Iterable[IntValue])
  extends IntInvariant(
    vars.foldLeft(0)((a:Int, b:IntValue) => a + b.value), 
    vars.foldLeft(0)((acc, intvar) => DomainHelper.safeAddMin(acc,intvar.min)) to vars.foldLeft(0)((acc, intvar) => DomainHelper.safeAddMax(acc,intvar.max))){

  for (v <- vars) registerStaticAndDynamicDependency(v)
  finishInitialization()

  override def notifyIntChanged(v: ChangingIntValue, OldVal: Int, NewVal: Int) {
    this :+= NewVal - OldVal
  }

  override def checkInternals(c: Checker) {
    c.check(this.value == vars.foldLeft(0)((acc, intvar) => acc + intvar.value),
      Some("output.value == vars.foldLeft(0)((acc,intvar) => acc+intvar.value)"))
  }
}

/**
 * linear(vars, coeffs)
 * @param vars is an iterable of IntVars
 * @param coeffs is an Indexed Sequence of Int
 * @author renaud.delandtsheer@cetic.be
 * @author jean-noel.monette@it.uu.se
 * */
class Linear(vars: Iterable[IntValue], coeffs: IndexedSeq[Int]) 
  extends IntInvariant(
		  vars.zip(coeffs).foldLeft(0)((a, bc) => a + bc._1.value*bc._2), 
		  vars.zip(coeffs).foldLeft(0)((acc, intvar) => DomainHelper.safeAddMin(acc,DomainHelper.getMinProd(intvar._1.minVal,intvar._1.maxVal,intvar._2,intvar._2))) to 
		  vars.zip(coeffs).foldLeft(0)((acc, intvar) => DomainHelper.safeAddMax(acc,DomainHelper.getMaxProd(intvar._1.minVal,intvar._1.maxVal,intvar._2,intvar._2)))){
  //coeffs needs to be indexed as we need to access it be index from the index of vars (as given in notifyIntChanged)
			  //TODO: There is still the risk of adding plus and minus "infinity" and get absurd results. But at least we avoid overflows...
			   
  vars.zipWithIndex.foreach(vi => registerStaticAndDynamicDependency(vi._1,vi._2))
  finishInitialization()
 

  override def notifyIntChanged(v: CBLSIntVar, idx: Int, OldVal: Int, NewVal: Int) {
    this :+= (NewVal - OldVal) * coeffs(idx)
  }

  override def checkInternals(c: Checker) {
    c.check(this.value == vars.zip(coeffs).foldLeft(0)((acc, intvar) => acc + intvar._1.value*intvar._2),
      Some("output.value == vars.zip(coeff).foldLeft(0)((acc, intvar) => acc + intvar._1.value*intvar._2)"))
  }
}

/**
 * prod(vars)
 * @param vars is a set of IntVars
 * @author renaud.delandtsheer@cetic.be
 * */
class Prod(vars: Iterable[IntValue]) extends IntInvariant {
  assert(vars.size > 0, "Invariant prod declared with zero vars to multiply")

  for (v <- vars) registerStaticAndDynamicDependency(v)
  finishInitialization()

  var NullVarCount: Int = vars.count(v => v.value == 0)
  var NonNullProd: Int = vars.foldLeft(1)((acc, intvar) => if (intvar.value == 0) { acc } else { acc * intvar.value })

  if (NullVarCount != 0) {
    this := 0
  } else {
    this := NonNullProd
  }

  //TODO: find better bound, this is far too much
  restrictDomain({
    val myMax = vars.foldLeft(1)((acc, intvar) => acc * (if (math.abs(intvar.max) > math.abs(intvar.min)) math.abs(intvar.max) else math.abs(intvar.min)))
    -myMax to myMax})

  @inline
  override def notifyIntChanged(v: ChangingIntValue, OldVal: Int, NewVal: Int) {
    assert(OldVal != NewVal)
    if (OldVal == 0 && NewVal != 0) {
      NullVarCount -= 1
      NonNullProd *= NewVal
    } else if (OldVal != 0 && NewVal == 0) {
      NullVarCount += 1
      NonNullProd = NonNullProd / OldVal
    } else {
      NonNullProd = NonNullProd / OldVal
      NonNullProd = NonNullProd * NewVal
    }
    if (NullVarCount == 0) {
      this := NonNullProd
    } else {
      this := 0
    }
  }

  override def checkInternals(c: Checker){
    var prod = 1
    for (v <- vars) prod *= v.value
    c.check(this.value == prod,
      Some("output.value (" + this.value + ") == prod (" + prod + ")"))
  }
}

/**
 * left - right
 * where left, right, and output are IntVar
 * @author renaud.delandtsheer@cetic.be
 * */
case class Minus(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, (l: Int, r: Int) => DomainHelper.safeSub(l,r), DomainHelper.safeSub(left.min, right.max) to DomainHelper.safeSub(left.max, right.min)) {
  assert(left != right)
}

/**
 * abs(left - right)
 * where left, right, and output are IntVar
 * @author jean-noel.monette@it.uu.se
 * */
case class Dist(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, 
      ((l: Int, r: Int) => DomainHelper.safeSub(l,r).abs), 
      {val v = DomainHelper.safeSub(left.min, right.max); (if (v <= 0) 0 else v)} to 
      DomainHelper.safeSub(left.max, right.min).max(DomainHelper.safeSub(right.max,left.min))) {
  assert(left != right)
}

/**
 * left + right
 * where left, right, and output are IntVar
 * @author renaud.delandtsheer@cetic.be
 * */
case class Sum2(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, ((l: Int, r: Int) => DomainHelper.safeAdd(l,r)), DomainHelper.safeAddMin(left.min, right.min) to DomainHelper.safeAddMax(left.max, right.max))

/**
 * left * right
 * where left, right, and output are IntVar
 * @author renaud.delandtsheer@cetic.be
 * */
case class Prod2(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, ((l: Int, r: Int) => DomainHelper.safeMult(l,r)), DomainHelper.getMinProd2(left, right), DomainHelper.getMaxProd2(left, right))

/**
 * left / right
 * where left, right, and output are IntVar
 * do not set right to zero, as usual...
 * @author renaud.delandtsheer@cetic.be
 * */
case class Div(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, (l: Int, r: Int) => l / r, DomainHelper.getMinDiv(left, right), DomainHelper.getMaxDiv(left, right))
/**
 * left / right
 * where left, right, and output are IntVar
 * do not set right to zero, as usual...
 * @author renaud.delandtsheer@cetic.be
 * */
case class Mod(left: IntValue, right: IntValue)
  extends IntInt2Int(left, right, (l: Int, r: Int) => l - r * (l / r), 0, Math.min(left.max, right.max))

/**
 * abs(v) (absolute value)
 * where output and v are IntVar
 * @author renaud.delandtsheer@cetic.be
 * */
case class Abs(v: IntValue)
  extends Int2Int(v, ((x: Int) => x.abs), (if (v.min <= 0) 0 else v.min) to v.max.max(-v.min))

/**
 * This invariant implements a step function. Values higher than pivot are mapped to ifval
 * values lower or equal to pivot are mapped to elseval
 * @author renaud.delandtsheer@cetic.be, suggested by Jean-Noël Monette
 *
 * @param x the IntVar parameter of the invariant
 * @param pivot the pivot value
 * @param thenval the value returned when x > pivot
 * @param elseval the value returned when x <= pivot
 */
case class Step(x: IntValue, pivot: Int = 0, thenval: Int = 1, elseval: Int = 0)
  extends Int2Int(x, (a: Int) => if (a > pivot) thenval else elseval, 0 to 1)

/**
 * This invariant implements the identity function within the min-max range.
 * values lower tham min result to min
 * values higher tham max result to max
 * @author renaud.delandtsheer@cetic.be
 * @param x
 * @param minBound
 * @param maxBound
 */
case class Bound(x: IntValue, minBound:Int, maxBound:Int)
  extends Int2Int(x, (a: Int) => if (a < minBound) minBound else if (a > maxBound) maxBound else a, math.max(minBound,x.min) to math.min(maxBound,x.max))


/**
 * @author Gustav Björdal
 */
object DomainHelper {
  def getMinDiv(left: CBLSIntVar, right: CBLSIntVar) = {
    val maxVal = if (right.maxVal == 0) { -1 } else { right.maxVal }
    val minVal = if (right.minVal == 0) { 1 } else { right.minVal }
    Math.min(left.minVal / maxVal, Math.min(left.minVal / minVal, Math.min(left.maxVal / maxVal, left.maxVal / minVal)))
  }
  def getMaxDiv(left: CBLSIntVar, right: CBLSIntVar) = {
    val maxVal = if (right.maxVal == 0) { -1 } else { right.maxVal }
    val minVal = if (right.minVal == 0) { 1 } else { right.minVal }
    Math.max(left.minVal / maxVal, Math.max(left.minVal / minVal, Math.max(left.maxVal / maxVal, left.maxVal / minVal)))
  }

  // Unfortunately all of these options need to be checked. For example if left has the domain -10..0 and right has the domain 3..5 then
  // the min value would be -50 and the max value would be 0. But if the domains were -10..0 and -10..0 then the min would be 0 and max 100. 
  // So basically all combinations of the domains min and max could yield the new min and max, as the ugly code below indicates. 
  def getMinProd2(left: CBLSIntVar, right: CBLSIntVar) = {
    Math.min(safeMult(left.minVal, right.minVal), Math.min(safeMult(left.minVal, right.maxVal), Math.min(safeMult(left.maxVal, right.minVal), safeMult(left.maxVal, right.maxVal))))
  }
  def getMinProd(lm:Int,lM:Int,rm:Int,rM:Int) = {
    Math.min(safeMult(lm, rm), Math.min(safeMult(lm, rM), Math.min(safeMult(lM,rm), safeMult(lM,rM))))
  }

  def getMaxProd2(left: CBLSIntVar, right: CBLSIntVar) = {
    Math.max(safeMult(left.minVal, right.minVal), Math.max(safeMult(left.minVal, right.maxVal), Math.max(safeMult(left.maxVal, right.minVal), safeMult(left.maxVal, right.maxVal))))
  }
  def getMaxProd(lm:Int,lM:Int,rm:Int,rM:Int) = {
    Math.max(safeMult(lm, rm), Math.max(safeMult(lm, rM), Math.max(safeMult(lM,rm), safeMult(lM,rM))))
  }
  //Safe addition
  def safeAdd(x: Int, y: Int): Int = {
    if (x.toLong + y.toLong > Int.MaxValue) {
      Int.MaxValue
    } else if (x.toLong + y.toLong < Int.MinValue) {
      Int.MinValue
    } else {
      x + y
    }
  }
  //Safe subtaction
  def safeSub(x: Int, y: Int): Int = {
    if (x.toLong - y.toLong > Int.MaxValue) {
      Int.MaxValue
    } else if (x.toLong - y.toLong < Int.MinValue) {
      Int.MinValue
    } else {
      x - y
    }
  }
  //Safe multiplication
  def safeMult(x: Int, y: Int): Int = {
    if (x.toLong * y.toLong > Int.MaxValue) {
      Int.MaxValue
    } else if (x.toLong * y.toLong < Int.MinValue) {
      Int.MinValue
    } else {
      x * y
    }
  }
  //Division of integers is always safe.
}

