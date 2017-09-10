package oscar.cp.searches.lns.search

import oscar.cp.searches.lns.operators.ALNSBuilder

/**
  * Search configuration
  */
class ALNSConfig(
                  val timeout: Long = 0, //Search timeout (ns)
                  val objective: Option[Int] = None, //Objective threshold to reach
                  val memLimit: Int = 1000, //Memory limit (mb)
                  val coupled: Boolean = false, //Coupled operators
                  val learning: Boolean = false, //Learning phase
                  val relaxOperatorKeys: Array[String], //The relax operators to use
                  val searchOperatorKeys: Array[String], //The search operators to use
                  val valLearn: Boolean = false, //Value learning heuristic
                  val opDeactivation: Boolean = false, //Wether Failing operators can be deactivated or not
                  val opSelectionKey: String = ALNSBuilder.RWheel, //The operators selection mechanism
                  val paramSelectionKey: String = ALNSBuilder.RWheel, //The parameters selection mechanism
                  val opMetricKey: String = ALNSBuilder.LastImprov, //The operators performance metric
                  val paramMetricKey: String = ALNSBuilder.LastImprov, //The parameters performance metric
                  val relaxSize: Array[Double] = ALNSBuilder.DefRelaxParam,
                  val nFailures: Array[Int] = ALNSBuilder.DefNFailures
                ) {
}
