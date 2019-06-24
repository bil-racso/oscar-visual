package oscar.cbls.business.routing.invariants.group

import oscar.cbls._
import oscar.cbls.algo.magicArray.IterableMagicBoolArray
import oscar.cbls.algo.quick.QList
import oscar.cbls.algo.seq.IntSequence
import oscar.cbls.business.routing.model.VehicleLocation
import oscar.cbls.core._

import scala.collection.immutable.HashMap

abstract class GlobalConstraintDefinitionV2[T : Manifest, U : Manifest] (routes: ChangingSeqValue, v: Long)
  extends Invariant with SeqNotificationTarget{

  var notifyTime = 0L
  var notifyCount = 0

  val n = routes.maxValue+1L
  val vehicles = 0L until v

  val preComputedValues: Array[T] = new Array[T](n)
  val vehicleValues: Array[U] = new Array[U](v)
  var vehicleValuesAtLevel0: Array[U] = new Array[U](v)

  var checkpointLevel: Int = -1
  var checkpointAtLevel0: IntSequence = _
  var changedVehiclesSinceCheckpoint0 = new IterableMagicBoolArray(v, false)
  //var changedVehiclesSinceLastCheckpoint = new IterableMagicBoolArray(v, false)

  // An array holding the ListSegment modifications as a QList
  // Int == checkpoint level, ListSegments the new ListSegment of the vehicle considering the modifications
  // It holds at least one value : the initial value whose checkpoint level == -1
  // Then each time we do a modification to the ListSegment, it's stored with the current checkpoint level
  var segmentsOfVehicle: Array[ListSegments] = Array.fill(v)(null)
  // Each time we define a checkpoint we save the current constraint state including :
  //    - changedVehiclesSinceCheckpoint0
  //    - vehiclesValueAtCheckpoint
  //    - vehicleSearcher
  //    - positionToValueCache
  var savedDataAtCheckPointLevel: QList[(QList[Int], Array[U], VehicleLocation, Array[Option[Int]], Array[ListSegments])] = null
  var positionToValueCache: Array[Option[Int]] = Array.fill(n)(None)

  var initialListSegmentOfVehicles: collection.immutable.Map[Int, ListSegments] = HashMap.empty

  protected var vehicleSearcher: VehicleLocation = VehicleLocation((0 until v).toArray)

  for (vehicle <- vehicles){
    val valueOfVehicle = computeVehicleValueFromScratch(vehicle,routes.value)
    assignVehicleValue(vehicle,valueOfVehicle)
    vehicleValues(vehicle) = valueOfVehicle
  }

  registerStaticAndDynamicDependency(routes)

  finishInitialization()

  for(outputVariable <- outputVariables)outputVariable.setDefiningInvariant(this)

  def outputVariables:Iterable[Variable]

  /**
    * tis method is called by the framework when a pre-computation must be performed.
    * you are expected to assign a value of type T to each node of the vehicle "vehicle" through the method "setNodeValue"
    * @param vehicle the vehicle where pre-computation must be performed
    * @param routes the sequence representing the route of all vehicle
    *               BEWARE,other vehicles are also present in this sequence; you must only work on the given vehicle
    * @param preComputedVals The array of precomputed values
    */
  def performPreCompute(vehicle:Long,
                        routes:IntSequence,
                        preComputedVals:Array[T])

  /**
    * this method is called by the framework when the value of a vehicle must be computed.
    *
    * @param vehicle the vehicle that we are focusing on
    * @param segments the segments that constitute the route.
    *                 The route of the vehicle is equal to the concatenation of all given segments in the order thy appear in this list
    * @param routes the sequence representing the route of all vehicle
    * @param preComputedVals The array of precomputed values
    * @return the value associated with the vehicle
    */
  def computeVehicleValue(vehicle:Long,
                          segments:QList[Segment[T]],
                          routes:IntSequence,
                          preComputedVals:Array[T]):U

  /**
    * the framework calls this method to assign the value U to he output variable of your invariant.
    * It has been dissociated from the method above because the framework memorizes the output value of the vehicle,
    * and is able to restore old value without the need to re-compute them, so it only will call this assignVehicleValue method
    * @param vehicle the vehicle number
    * @param value the value of the vehicle
    */
  def assignVehicleValue(vehicle:Long,value:U)

  /**
    * this method is defined for verification purpose. It computes the value of the vehicle from scratch.
    *
    * @param vehicle the vehicle on which the value is computed
    * @param routes the sequence representing the route of all vehicle
    * @return the value of the constraint for the given vehicle
    */
  def computeVehicleValueFromScratch(vehicle : Long, routes : IntSequence):U

  override def notifySeqChanges(r: ChangingSeqValue, d: Int, changes: SeqUpdate): Unit = {
    val start = System.nanoTime()
    if(digestUpdates(changes) && !(this.checkpointLevel == -1)){
      val newRoute = routes.newValue
      QList.qForeach(changedVehiclesSinceCheckpoint0.indicesAtTrueAsQList,(vehicle: Int) => {
        // Compute new vehicle value based on last segment changes
        vehicleValues(vehicle) = computeVehicleValue(vehicle, segmentsOfVehicle(vehicle).segments, newRoute, preComputedValues)
        assignVehicleValue(vehicle, vehicleValues(vehicle))
      })
    } else {
      for (vehicle <- vehicles){
        assignVehicleValue(vehicle,computeVehicleValueFromScratch(vehicle,routes.value))
      }
    }
    notifyTime += System.nanoTime() - start
    notifyCount += 1
  }

  private def digestUpdates(changes:SeqUpdate): Boolean = {
    changes match {
      case SeqUpdateDefineCheckpoint(prev: SeqUpdate,isStarMode: Boolean,checkpointLevel: Int) =>
        val newRoute = changes.newValue
        val prevUpdate = digestUpdates(prev)

        // Either we got an assign update or this is the very first define checkpoint ==> We must init all vehicles
        if(!prevUpdate || this.checkpointLevel < 0) {
          checkpointAtLevel0 = newRoute // Save the state of the route for further comparison
          computeAndAssignVehiclesValueFromScratch(newRoute)
          (0 until v).foreach(vehicle => {
            performPreCompute(vehicle, newRoute, preComputedValues)
            vehicleValuesAtLevel0(vehicle) = vehicleValues(vehicle)
            initSegmentsOfVehicle(vehicle, newRoute)
          })
          // Defining checkpoint 0 ==> we must init every changed since checkpoint 0 vehicles
        } else if(checkpointLevel == 0) {
          checkpointAtLevel0 = newRoute // Save the state of the route for further comparison
          // Computing init ListSegment value for each vehicle that has changed
          QList.qForeach(changedVehiclesSinceCheckpoint0.indicesAtTrueAsQList,(vehicle: Int) => {
            performPreCompute(vehicle, newRoute, preComputedValues)
            vehicleValuesAtLevel0(vehicle) = vehicleValues(vehicle)
            initSegmentsOfVehicle(vehicle, newRoute)
          })

          // Persisting recent updates of the vehicleSearcher
          vehicleSearcher = vehicleSearcher.regularize
          // Resetting the savedData QList.
          savedDataAtCheckPointLevel = null

          changedVehiclesSinceCheckpoint0.all = false

          // Defining another checkpoint. We must keep current segments state
        } else {
          // Saving position of nodes of the previous checkpoint level to avoid excessive calls to positionAtAnyOccurence(...) when roll-backing
          val previousCheckpointSaveData = savedDataAtCheckPointLevel.head
          savedDataAtCheckPointLevel = QList(
            (previousCheckpointSaveData._1, previousCheckpointSaveData._2, previousCheckpointSaveData._3, positionToValueCache, previousCheckpointSaveData._5),
            savedDataAtCheckPointLevel.tail)
        }

        // Common manipulations
        this.checkpointLevel = checkpointLevel
        positionToValueCache = Array.fill(n)(None)
        savedDataAtCheckPointLevel =
          QList((changedVehiclesSinceCheckpoint0.indicesAtTrueAsQList, vehicleValues.clone(), vehicleSearcher, positionToValueCache, segmentsOfVehicle),savedDataAtCheckPointLevel)
        segmentsOfVehicle = segmentsOfVehicle.clone()
        //changedVehiclesSinceLastCheckpoint.all = false
        true

      case r@SeqUpdateRollBackToCheckpoint(checkpoint:IntSequence,checkpointLevel:Int) =>
        if(checkpointLevel == 0L) {
          require(checkpoint quickEquals this.checkpointAtLevel0)
        }

        // This variable holds the vehicles that changes during this roll-back
        // We must at least revert changes since last checkpoint
        //val vehiclesChangedDuringRollBack = changedVehiclesSinceLastCheckpoint

        // While the checkpoint level of segmentsOfVehicles(v) head is greater or equal to checkpointLevel, pop the head
        // Then recompute vehicle value and assign value
        /*def revertSegmentChangesSinceCheckpointAndUpdateVehicleValues(): Unit ={
          QList.qForeach(vehiclesChangedDuringRollBack.indicesAtTrueAsQList, (vehicle: Int) => {
            while(segmentsOfVehicle(vehicle).head._1 >= checkpointLevel) {
              segmentsOfVehicle(vehicle) = segmentsOfVehicle(vehicle).tail
            }
            // TODO : It's not a problem to reset the vehicle value to 0 because
            //  we re-compute all changed values since chkpoint 0 in notifySeqChanges
            //  BUT ! If we want to avoid recomputing all changed vehicle since ckpoint 0 each time
            //  We should lazy-save the vehicle value at checkpoint x for each changing vehicle
            vehicleValues(vehicle) = vehicleValuesAtLevel0(vehicle)
            assignVehicleValue(vehicle, vehicleValuesAtLevel0(vehicle))
          })
        }*/

        // savedDataAtCheckpointLevel holds some information stored when defining checkpoint
        // Since the fact that there is only one saved value per checkpoint,
        // in order to restore the right information, the head of savedDataAtCheckpointLevel
        // should be checkpointLevel we are rollbacking to.
        /*while(savedDataAtCheckPointLevel.tail != null  && savedDataAtCheckPointLevel.head._1 != checkpointLevel) {
          QList.qForeach(savedDataAtCheckPointLevel.head._3,
            (vehicle: Int) => vehiclesChangedDuringRollBack(vehicle) = true)     // Mark all changed vehicle of this checkpoint level to be revert
          savedDataAtCheckPointLevel = savedDataAtCheckPointLevel.tail    // Taking previous checkpoint save
        }*/
        savedDataAtCheckPointLevel = QList.qDrop(savedDataAtCheckPointLevel, this.checkpointLevel - checkpointLevel)
        //revertSegmentChangesSinceCheckpointAndUpdateVehicleValues()
        val vehicleValueAtCheckpointLevel = savedDataAtCheckPointLevel.head._2
        QList.qForeach(changedVehiclesSinceCheckpoint0.indicesAtTrueAsQList, (vehicle: Int) => {
          vehicleValues(vehicle) = vehicleValueAtCheckpointLevel(vehicle)
          assignVehicleValue(vehicle, vehicleValues(vehicle))
        })
        this.changedVehiclesSinceCheckpoint0.all = false
        QList.qForeach(savedDataAtCheckPointLevel.head._1, (vehicle: Int) =>  this.changedVehiclesSinceCheckpoint0(vehicle) = true)
        vehicleSearcher = savedDataAtCheckPointLevel.head._3
        positionToValueCache = savedDataAtCheckPointLevel.head._4
        segmentsOfVehicle = savedDataAtCheckPointLevel.head._5.clone()

        this.checkpointLevel = checkpointLevel
        true

      case sui@SeqUpdateInsert(value : Long, pos : Int, prev : SeqUpdate) =>
        if(digestUpdates(prev)){
            keepOrResetPositionValueCache(prev)

            val prevRoutes = prev.newValue

            val impactedVehicle = vehicleSearcher.vehicleReachingPosition(pos-1)
            val impactedSegment = segmentsOfVehicle(impactedVehicle)

            // InsertSegment insert a segment AFTER a defined position and SeqUpdateInsert at a position => we must withdraw 1
            segmentsOfVehicle(impactedVehicle) = impactedSegment.insertSegments(QList[Segment[T]](NewNode(value).asInstanceOf[Segment[T]]),pos-1,prevRoutes)

            vehicleSearcher = vehicleSearcher.push(sui.oldPosToNewPos)
            changedVehiclesSinceCheckpoint0(impactedVehicle) = true
            true
        } else {
          false
        }

      case sum@SeqUpdateMove(fromIncluded : Int, toIncluded : Int, after : Int, flip : Boolean, prev : SeqUpdate) =>
        if(digestUpdates(prev)) {
          keepOrResetPositionValueCache(prev)

          val prevRoutes = prev.newValue

          val fromVehicle = vehicleSearcher.vehicleReachingPosition(fromIncluded)
          val toVehicle = vehicleSearcher.vehicleReachingPosition(after)
          val sameVehicle = fromVehicle == toVehicle

          val fromImpactedSegment = segmentsOfVehicle(fromVehicle)

          // Identification of the sub-segments to remove
          val (listSegmentsAfterRemove, segmentsToRemove) =
            fromImpactedSegment.removeSubSegments(fromIncluded, toIncluded, prevRoutes)

          val toImpactedSegment = if (sameVehicle) listSegmentsAfterRemove else segmentsOfVehicle(toVehicle)
          // If we are in same vehicle and we remove nodes to put them later in the route, the route length before insertion point has shortened
          val delta =
            if (!sameVehicle || after < fromIncluded) 0
            else toIncluded - fromIncluded + 1

          // Insert the sub-segments at his new position
          val listSegmentsAfterInsertion =
            if (flip)
              toImpactedSegment.insertSegments(segmentsToRemove.qMap(_.flip).reverse, after, prevRoutes, delta)
            else
              toImpactedSegment.insertSegments(segmentsToRemove, after, prevRoutes, delta)

          segmentsOfVehicle(toVehicle) = listSegmentsAfterInsertion
          if (!sameVehicle) segmentsOfVehicle(fromVehicle) = listSegmentsAfterRemove

          vehicleSearcher = vehicleSearcher.push(sum.oldPosToNewPos)

          changedVehiclesSinceCheckpoint0(fromVehicle) = true
          changedVehiclesSinceCheckpoint0(toVehicle) = true
          true
        } else {
          false
        }

      case sur@SeqUpdateRemove(position : Int, prev : SeqUpdate) =>
        if(digestUpdates(prev)) {
          keepOrResetPositionValueCache(prev)

          val prevRoutes = prev.newValue

          val impactedVehicle = vehicleSearcher.vehicleReachingPosition(position)
          val impactedSegment = segmentsOfVehicle(impactedVehicle)

          val (listSegmentAfterRemove, _) = impactedSegment.removeSubSegments(position, position, prevRoutes)

          segmentsOfVehicle(impactedVehicle) = listSegmentAfterRemove

          vehicleSearcher = vehicleSearcher.push(sur.oldPosToNewPos)
          changedVehiclesSinceCheckpoint0(impactedVehicle) = true
          true
        } else {
          false
        }

      case SeqUpdateLastNotified(value:IntSequence) =>
        require(value quickEquals routes.value)
        true

      case SeqUpdateAssign(value : IntSequence) =>
        false //impossible to go incremental
    }
  }

  private def keepOrResetPositionValueCache(prev: SeqUpdate): Unit ={
    prev match {
      case _:SeqUpdateInsert => positionToValueCache = Array.fill(n)(None)
      case _:SeqUpdateMove => positionToValueCache = Array.fill(n)(None)
      case _:SeqUpdateRemove => positionToValueCache = Array.fill(n)(None)
      case _ =>
    }
  }

  private def initSegmentsOfVehicle(vehicle: Int, route: IntSequence): Unit ={
    val posOfVehicle = vehicleSearcher.startPosOfVehicle(vehicle)
    val (lastNodeOfVehicle, posOfLastNodeOfVehicle) =
      if(vehicle < v-1) {
        val lastNodePos = vehicleSearcher.startPosOfVehicle(vehicle+1)-1
        (route.valueAtPosition(lastNodePos).get,lastNodePos)
      }
      else {
        (route.valueAtPosition(route.size-1).get,route.size-1)
      }

    segmentsOfVehicle(vehicle) = ListSegments(
      QList[Segment[T]](PreComputedSubSequence(
        vehicle,
        preComputedValues(vehicle),
        lastNodeOfVehicle,
        preComputedValues(lastNodeOfVehicle),
        posOfLastNodeOfVehicle - posOfVehicle + 1
      )),
      vehicle)
  }

  private def computeAndAssignVehiclesValueFromScratch(newSeq: IntSequence): Unit ={
    vehicles.foreach(v => {
      val start = System.nanoTime()
      assignVehicleValue(v, computeVehicleValueFromScratch(v, newSeq))
    })
  }

  private def preComputedToString():String = {
    "[" + preComputedValues.indices.map(i => "\n\t" + i + ":" + preComputedValues(i)).mkString("") + "\n]"
  }

  override def checkInternals(c : Checker): Unit = {
    for (v <- vehicles){
      require(computeVehicleValueFromScratch(v,routes.value).equals(vehicleValues(v)),
        "For Vehicle " + v + " : " + computeVehicleValueFromScratch(v,routes.value) + " " +
          vehicleValues(v) + " " + routes + "\n" + preComputedToString())
    }
  }

  object ListSegments{
    def apply(segments: QList[Segment[T]], vehicle: Int): ListSegments = new ListSegments(segments, vehicle)

    def apply(listSegments: ListSegments): ListSegments =
      new ListSegments(listSegments.segments, listSegments.vehicle)
  }

  class ListSegments(val segments: QList[Segment[T]], val vehicle: Int){

    /**
      * Remove Segments and Sub-Segment from the Segments List
      *
      * Find the Segments holding the specified positions and split them in two parts right after these positions.
      * If a position is at the end of a Segment, this Segment is not splitted.
      * The in-between segments are gathered as well
      * @param from From position
      * @param to To position
      * @return A ListSegments containing the segments containing or between from and to
      */
    def removeSubSegments(from: Int, to: Int, routes: IntSequence): (ListSegments, QList[Segment[T]]) ={
      val vehiclePos = vehicleSearcher.startPosOfVehicle(vehicle)
      val (fromImpactedSegment, segmentsBeforeFromImpactedSegment, segmentsAfterFromImpactedSegment, currentCounter) =
        findImpactedSegment(from,vehicle,vehiclePos-1)
      val (toImpactedSegment, segmentsBetweenFromAndTo, segmentsAfterToImpactedSegment,_) =
        findImpactedSegment(to,vehicle, currentCounter,QList(fromImpactedSegment,segmentsAfterFromImpactedSegment))

      // nodeBeforeFrom is always defined because it's at worst a vehicle node
      val (nodeBeforeFrom, fromNode, toNode, nodeAfterTo) = {
        val beforeFromExplorer = if(positionToValueCache(from-1).isEmpty)routes.explorerAtPosition(from-1) else None
        val fromExplorer = if(positionToValueCache(from).isEmpty){
          if(beforeFromExplorer.nonEmpty)
            beforeFromExplorer.get.next
          else
            routes.explorerAtPosition(from)
        } else None

        val toExplorer = if(positionToValueCache(to).isEmpty) routes.explorerAtPosition(to) else None

        if(beforeFromExplorer.nonEmpty)
          positionToValueCache(from-1) = Some(beforeFromExplorer.get.value)
        if(fromExplorer.nonEmpty)
          positionToValueCache(from) = Some(fromExplorer.get.value)
        if(toExplorer.nonEmpty)
          positionToValueCache(to) = Some(toExplorer.get.value)
        if(to+1 < n && positionToValueCache(to+1).isEmpty){
          val afterToExplorer =
            if(toExplorer.nonEmpty)
              toExplorer.get.next
            else
              routes.explorerAtPosition(to+1)
          positionToValueCache(to+1) = Some(if(afterToExplorer.isEmpty) -1 else afterToExplorer.get.value)
        }
        (positionToValueCache(from-1).get,
          positionToValueCache(from).get,
          positionToValueCache(to).get,
          if(to+1 < n)positionToValueCache(to+1).get else -1)
      }

      val lengthUntilFromImpactedSegment = QList.qFold[Segment[T],Long](segmentsBeforeFromImpactedSegment, (acc,item) => acc + item.length(),0)

      val (leftResidue, removedSegments, rightResidue) =
      // 1° The removed segment is included in one segment
        if(fromImpactedSegment == toImpactedSegment) {
          val fromLeftResidueLength = from - lengthUntilFromImpactedSegment - vehiclePos
          val fromRightResidueLength = fromImpactedSegment.length() - fromLeftResidueLength

          val toRightResidueLength =
            if (nodeAfterTo == -1) 0
            else fromRightResidueLength - to + from - 1
          val toLeftResidueLength =
            fromRightResidueLength - toRightResidueLength

          // The left-remaining part of the initial segment
          val (leftResidue, leftCuttedInitialSegment: Option[Segment[T]]) =
            fromImpactedSegment.splitAtNode(nodeBeforeFrom, fromNode, preComputedValues(nodeBeforeFrom), preComputedValues(fromNode), fromLeftResidueLength, fromRightResidueLength)
          // The right-remaining part of the initial segment
          val (removedSegments: Option[Segment[T]], rightResidue) =
            if (nodeAfterTo >= 0)
              leftCuttedInitialSegment.get.splitAtNode(toNode, nodeAfterTo, preComputedValues(toNode), preComputedValues(nodeAfterTo), toLeftResidueLength, toRightResidueLength)
            else
              (leftCuttedInitialSegment, None)

          (leftResidue, QList(removedSegments.get), rightResidue)
        } else {
          val lengthToFromImpactedSegment =
            lengthUntilFromImpactedSegment +
              fromImpactedSegment.length()
          val lengthToToImpactedSegment =
            lengthUntilFromImpactedSegment +
              QList.qFold[Segment[T],Long](segmentsBetweenFromAndTo, (acc,item) => acc + item.length(),0) +
              toImpactedSegment.length()

          val toRightResidueLength =
            if (nodeAfterTo == -1) 0
            else lengthToToImpactedSegment - to + vehiclePos
          val toLeftResidueLength =
            lengthToToImpactedSegment - toRightResidueLength

          val fromRightResidueLength = lengthToFromImpactedSegment - from + vehiclePos
          val fromLeftResidueLength = lengthToFromImpactedSegment - fromRightResidueLength

          // The left-remaining part of the initial from segment
          val (leftResidue, leftCuttedFromSegment: Option[Segment[T]]) =
            fromImpactedSegment.splitAtNode(nodeBeforeFrom, fromNode, preComputedValues(nodeBeforeFrom), preComputedValues(fromNode), fromLeftResidueLength, fromRightResidueLength)
          // The right-remaining part of the initial to segment
          val (rightCuttedToSegment: Option[Segment[T]], rightResidue) =
            if (nodeAfterTo >= 0)
              toImpactedSegment.splitAtNode(toNode, nodeAfterTo, preComputedValues(toNode), preComputedValues(nodeAfterTo), toLeftResidueLength, toRightResidueLength)
            else
              (toImpactedSegment, None)

          var removedSegments =
            QList(leftCuttedFromSegment.get, segmentsBetweenFromAndTo.tail)
          if (rightCuttedToSegment.nonEmpty)
            removedSegments = QList.nonReversedAppend(removedSegments, QList(rightCuttedToSegment.get))

          (leftResidue, removedSegments, rightResidue)
        }

      var newSegments: QList[Segment[T]] = segmentsAfterToImpactedSegment
      if(rightResidue.nonEmpty) newSegments = QList(rightResidue.get, newSegments)
      if(leftResidue.nonEmpty) newSegments = QList(leftResidue.get, newSegments)
      newSegments = QList.nonReversedAppend(segmentsBeforeFromImpactedSegment, newSegments)

      (ListSegments(newSegments,vehicle),removedSegments)
    }

    /**
      * Insert a list of segments at the specified position
      * @param segmentsToInsert
      * @param afterPosition
      * @return
      */
    def insertSegments(segmentsToInsert: QList[Segment[T]], afterPosition: Int, routes: IntSequence, delta: Long = 0): ListSegments ={
      val vehiclePos = vehicleSearcher.startPosOfVehicle(vehicle)

      val (impactedSegment, segmentsBeforeImpactedSegment, segmentsAfterImpactedSegment,_) = findImpactedSegment(afterPosition - delta, vehicle, vehiclePos-1)

      val (insertAfterNode, insertBeforeNode) = {
        val afterExplorer = if(positionToValueCache(afterPosition).isEmpty) routes.explorerAtPosition(afterPosition) else None

        if(afterExplorer.nonEmpty)
          positionToValueCache(afterPosition) = Some(afterExplorer.get.value)
        if(afterPosition+1 < n && positionToValueCache(afterPosition+1).isEmpty){
          val beforeExplorer = if(afterExplorer.nonEmpty)
            afterExplorer.get.next
          else
            routes.explorerAtPosition(afterPosition+1)
          positionToValueCache(afterPosition+1) = Some(if(beforeExplorer.isEmpty)-1 else beforeExplorer.get.value)
        }
        (positionToValueCache(afterPosition).get, if(afterPosition+1 < n)positionToValueCache(afterPosition+1).get else -1)
      }

      val rightResidueLength =
        if(insertBeforeNode == -1) 0
        else
          QList.qFold[Segment[T],Long](segmentsBeforeImpactedSegment, (acc,item) => acc + item.length(),0) +
            impactedSegment.length() - afterPosition + vehiclePos - 1 + delta
      val leftResidueLength = impactedSegment.length() - rightResidueLength

      // We need to split the impacted segment in two and insert the new Segment between those two sub segment
      // If we are at the end of a vehicle route, we don't split the route
      val (leftResidue: Option[Segment[T]], rightResidue: Option[Segment[T]]) =
      if(insertBeforeNode >= v)
        impactedSegment.splitAtNode(insertAfterNode,insertBeforeNode,
          preComputedValues(insertAfterNode),preComputedValues(insertBeforeNode),leftResidueLength, rightResidueLength)
      else
        (Some(impactedSegment),None)

      var newSegments: QList[Segment[T]] = segmentsAfterImpactedSegment
      if(rightResidue.nonEmpty) {
        newSegments = QList(rightResidue.get, newSegments)
      }
      newSegments = QList.nonReversedAppend(segmentsToInsert, newSegments)
      if(leftResidue.nonEmpty) {
        newSegments = QList(leftResidue.get, newSegments)
      }

      newSegments = QList.nonReversedAppend(segmentsBeforeImpactedSegment, newSegments)

      ListSegments(newSegments, vehicle)
    }

    /**
      * This method finds the impacted segment of the previous update.
      * The segment is found if the endNode is after or equal to the search position.
      *
      * @param pos the searched position
      * @param vehicle the vehicle in which we want to add a node
      * @return a tuple (impactedSegment: Segment[T], exploredSegments: Option[QList[Segment[T] ] ], unexploredSegments: Option[QList[Segment[T] ] ])
      */
    private def findImpactedSegment(pos: Long, vehicle: Int, initCounter: Long, segmentsToExplore: QList[Segment[T]] = segments): (Segment[T], QList[Segment[T]], QList[Segment[T]], Int) ={
      def checkSegment(segmentsToExplore: QList[Segment[T]], counter: Int = initCounter, exploredSegments: QList[Segment[T]] = null): (Segment[T], QList[Segment[T]], QList[Segment[T]], Int) ={
        require(segmentsToExplore != null, "Shouldn't happen, it means that the desired position is not within this vehicle route")
        val segment = segmentsToExplore.head
        val newCounter = counter + segment.length
        if(newCounter >= pos)
          (segment, if(exploredSegments != null) exploredSegments.reverse else exploredSegments, segmentsToExplore.tail, counter)
        else
          checkSegment(segmentsToExplore.tail, newCounter, QList(segment,exploredSegments))
      }

      checkSegment(segmentsToExplore)
    }

    def length(): Long ={
      QList.qFold[Segment[T], Long](segments, (acc, item) => acc + item.length, 0L)
    }

    override def toString: String ={
      "Segments of vehicle " + vehicle + " : " + segments.mkString(", ")
    }
  }

}

trait Segment[@specialized T]{
  /**
    * Split this Segment in two Segments right before the split node
    * If split node == start node, there will only be one Segment, the Segment itself
    * @return the left part of the splitted Segment (if exist) and the right part (starting at splitNode)
    */
  def splitAtNode(beforeSplitNode: Long, splitNode: Long, valueBeforeSplitNode: T, valueAtSplitNode: T, leftLength: Long, rightLength: Long): (Option[Segment[T]],Option[Segment[T]])

  def flip(): Segment[T]

  def length(): Long

  def startNode(): Long

  def endNode(): Long
}

/**
  * This represents a subsequence starting at startNode and ending at endNode.
  * This subsequence was present in the global sequence when the pre-computation was performed
  * @param startNode the first node of the subsequence
  * @param startNodeValue the T value that the pre-computation associated with the node "startNode"
  * @param endNode the last node of the subsequence
  * @param endNodeValue the T value that the pre-computation associated with the node "endNode"
  * @tparam T the type of precomputation
  */
case class PreComputedSubSequence[@specialized T](startNode:Long,
                                                  startNodeValue:T,
                                                  endNode:Long,
                                                  endNodeValue:T,
                                                  length: Long) extends Segment[T]{
  override def toString: String = {
    "PreComputedSubSequence (StartNode : " + startNode + " - value : " + startNodeValue + " EndNode : " + endNode + " - value " + endNodeValue + " Length : " + length + ")"
  }

  override def splitAtNode(beforeSplitNode: Long, splitNode: Long, valueBeforeSplitNode: T, valueAtSplitNode: T, leftLength: Long, rightLength: Long): (Option[Segment[T]],Option[Segment[T]]) = {
    if(splitNode == startNode) (None,Some(this))
    else if(beforeSplitNode == endNode) (Some(this),None)
    else {
      (Some(PreComputedSubSequence(startNode,startNodeValue,beforeSplitNode,valueBeforeSplitNode,leftLength)),
        Some(PreComputedSubSequence(splitNode,valueAtSplitNode,endNode,endNodeValue,rightLength)))
    }
  }

  override def flip(): Segment[T] = {
    FlippedPreComputedSubSequence(endNode, endNodeValue, startNode, startNodeValue, length)
  }
}

/**
  * This represents a subsequence starting at startNode and ending at endNode.
  * This subsequence was not present in the global sequence when the pre-computation was performed, but
  * the flippedd subsequence obtained by flippig it was present in the global sequence when the pre-computation was performed, but
  * @param startNode the first node of the subsequence (it was after the endNode when pre-computation ws performed)
  * @param startNodeValue the T value that the pre-computation associated with the node "startNode"
  * @param endNode the last node of the subsequence (it was before the endNode when pre-computation ws performed)
  * @param endNodeValue the T value that the pre-computation associated with the node "endNode"
  * @tparam T the type of precomputation
  */
case class FlippedPreComputedSubSequence[@specialized T](startNode:Long,
                                                         startNodeValue:T,
                                                         endNode:Long,
                                                         endNodeValue:T,
                                                         length: Long) extends Segment[T]{
  override def toString: String = {
    "FlippedPreComputedSubSequence (StartNode : " + startNode + " - value : " + startNodeValue + " EndNode : " + endNode + " - value " + endNodeValue + ")"
  }

  override def splitAtNode(beforeSplitNode: Long, splitNode: Long, valueBeforeSplitNode: T, valueAtSplitNode: T, leftLength: Long, rightLength: Long): (Option[Segment[T]],Option[Segment[T]]) = {
    if(splitNode == startNode) (None,Some(this))
    else if(beforeSplitNode == endNode) (Some(this),None)
    else {
      (Some(FlippedPreComputedSubSequence(startNode,startNodeValue,beforeSplitNode,valueBeforeSplitNode,leftLength)),
        Some(FlippedPreComputedSubSequence(splitNode,valueAtSplitNode,endNode,endNodeValue,rightLength)))
    }
  }

  override def flip(): Segment[T] = {
    PreComputedSubSequence(endNode, endNodeValue, startNode, startNodeValue, length)
  }
}

/**
  * This represent that a node that was not present in the initial sequence when pre-computation was performed.
  * @param node
  */
case class NewNode[@specialized T](node:Long) extends Segment[T]{
  override def toString: String = {
    "NewNode - Node : " + node
  }

  override def splitAtNode(beforeSplitNode: Long, splitNode: Long, valueBeforeSplitNode: T, valueAtSplitNode: T, leftLength: Long, rightLength: Long): (Option[Segment[T]],Option[Segment[T]]) = {
    require(beforeSplitNode == node)
    (Some(this), None)
  }

  override def flip(): Segment[T] = {
    this
  }

  override def length(): Long = 1L

  override def startNode(): Long = node

  override def endNode(): Long = node
}
