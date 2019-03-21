package oscar.cbls.test.scheduling

import oscar.cbls.Store
import oscar.cbls.business.seqScheduling.model._
import oscar.cbls.business.seqScheduling.neighborhood.{ReinsertActivity, SwapActivity}
import oscar.cbls.core.propagation.ErrorChecker
import oscar.cbls.lib.search.combinators.{BestSlopeFirst, Profile}

object SeqScheduling {
  // Model
  // Running Modes
  val m0 = RunningMode_B("0")
  val m1 = RunningMode_B("1")
  // Resources
  val r = Resource_B("R", 3)
    .withMode(m0)
    .withMode(m1, true)
    .setupTime(m0, m1, 1)
    .setupTime(m1, m0, 1)
  val r1 = Resource_B("R'", 1)
    .withMode(m0, true)
    .withMode(m1)
    .setupTime(m0, m1, 1)
    .setupTime(m1, m0, 4)
  // Activities
  lazy val a = Activity_B("A", 4)
    .withResource(r, 2, m1)
    .precedes(c)
  lazy val b = Activity_B("B", 1)
    .withResource(r, 1, m1)
    .withResource(r1, 1, m0)
  lazy val c = Activity_B("C", 3)
    .withResource(r, 1, m1)
    .withResource(r1, 1, m1)
    .precedes(e)
  lazy val d = Activity_B("D", 4)
    .withResource(r, 2, m1)
  lazy val e = Activity_B("E", 2)
    .withResource(r, 2, m0)
    .withResource(r1, 1, m0)

  def main(args: Array[String]): Unit = {
    // CBLS store
    val m = new Store(checker = Some(ErrorChecker()))
    // Create Scheduling Problem from model
    val scProblem = new SchedulingProblem_B(m, Set(a, b, c, d, e), Set(r, r1))
    m.close()
    println("Model closed.")
    // Neighborhoods
    val swapNH = new SwapActivity(scProblem, "Swap")
    val reinsertNH = new ReinsertActivity(scProblem, "Reinsert")
    val combinedNH = BestSlopeFirst(List(Profile(reinsertNH), Profile(swapNH)))
    // This is the search strategy
    combinedNH.doAllMoves(obj = scProblem.mkspObj)
    // And here, the results
    println(combinedNH.profilingStatistics)
    println(s"*************** RESULTS ***********************************")
    println(s"Schedule makespan = ${scProblem.makeSpan.value}")
    println(s"Scheduling sequence = ${scProblem.activitiesPriorList.value.toList}")
    println("Scheduling start times = [  ")
    scProblem.startTimes.foreach(v => println(s"    $v"))
    println("]")
    println(s"Scheduling setup times: ${scProblem.setupTimes}")
  }

  /*
  // CBLS store
  val m = new Store(checker = Some(ErrorChecker()))
  // Activities
  val a = new Activity(m, "a", 4)
  val b = new Activity(m, "b", 1)
  val c = new Activity(m, "c", 3)
  val d = new Activity(m, "d", 4)
  val e = new Activity(m, "e", 2)
  val activities = new BoundedArray[Activity](5, Activity.setIndex)
  activities.:::(List(e, d, c, b, a))
  // Resource R
  // Running Modes for R
  val runningModesForR = new RunningModeResources(2)
  val r0m0 = new RunningMode("R-0", 0)
  val r0m1 = new RunningMode("R-1", 0)
  runningModesForR.addRunningMode(r0m0)
  runningModesForR.addRunningMode(r0m1, true)
  runningModesForR.addSetupTime(r0m0, r0m1, 1)
  runningModesForR.addSetupTime(r0m1, r0m0, 1)
  val res0 = new Resource(m, "Resource R", 3, runningModesForR)
  // Resource R'
  // Running modes for R'
  val runningModesForR1 = new RunningModeResources(2)
  val r1m0 = new RunningMode("R'-0", 0)
  val r1m1 = new RunningMode("R'-1", 0)
  runningModesForR1.addRunningMode(r1m0, true)
  runningModesForR1.addRunningMode(r1m1)
  runningModesForR1.addSetupTime(r1m0, r1m1, 1)
  runningModesForR1.addSetupTime(r1m1, r1m0, 4)
  val res1 = new Resource(m, "Resource R'", 1, runningModesForR1)
  // All the resources
  val resources = new BoundedArray[Resource](2, Resource.setIndex)
  resources.:::(List(res0, res1))
  // Precedences
  val precedences = new Precedences(5)
  precedences.addPrecedence(a, c)
  precedences.addPrecedence(c, e)
  // Resource usages
  val resUsages = new ActivityResourceUsages(5, 2)
  resUsages.addActivityResourceUsage(a, res0, r0m1, 2)
  resUsages.addActivityResourceUsage(b, res0, r0m1, 1)
  resUsages.addActivityResourceUsage(c, res0, r0m1, 1)
  resUsages.addActivityResourceUsage(d, res0, r0m1, 2)
  resUsages.addActivityResourceUsage(e, res0, r0m0, 2)
  resUsages.addActivityResourceUsage(b, res1, r1m0, 1)
  resUsages.addActivityResourceUsage(c, res1, r1m1, 1)
  resUsages.addActivityResourceUsage(e, res1, r1m0, 1)
  // Scheduling Problem
  val scProblem = new SchedulingProblem(m, activities, resources, precedences, resUsages)
  // Model closed
  m.close()
  println("Model closed.")

  def main(args: Array[String]): Unit = {
    // Neighborhoods
    val swapNH = new SwapActivity(scProblem, "Swap")
    val reinsertNH = new ReinsertActivity(scProblem, "Reinsert")
    val combinedNH = BestSlopeFirst(List(Profile(reinsertNH), Profile(swapNH)))
    // This is the search strategy
    combinedNH.doAllMoves(obj = scProblem.mkspObj)
    // And here, the results
    println(combinedNH.profilingStatistics)
    println(s"*************** RESULTS ***********************************")
    println(s"Schedule makespan = ${scProblem.makeSpan.value}")
    println(s"Scheduling sequence = ${scProblem.activitiesPriorList.value.toList}")
    println("Scheduling start times = [  ")
    scProblem.startTimes.foreach(v => println(s"    $v"))
    println("]")
    println(s"Scheduling setup times: ${scProblem.setupTimes}")
  }
  */
}
