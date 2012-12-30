package oscar.cp.mem.pareto

case class MOSol[Sol](sol: Sol, objs: Array[Int]) {
  
  var tabu = 0
  
  def dominates(x: MOSol[Sol]): Boolean = dominates0(x, 0)
  
  private def dominates0(x: MOSol[Sol], i: Int): Boolean = {
    if (i == objs.size) true
    else if (x.objs(i) < objs(i)) false
    else dominates0(x, i+1)
  }
  
  override def toString: String = "MOSol("+objs.mkString(", ")+")"
}

object MOSol {  
  def apply[Sol](sol: Sol, objs: Int*) = new MOSol[Sol](sol, objs.toArray) 
}