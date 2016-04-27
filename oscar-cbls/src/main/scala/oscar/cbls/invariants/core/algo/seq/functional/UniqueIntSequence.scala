package oscar.cbls.invariants.core.algo.seq.functional

import oscar.cbls.invariants.core.algo.fun.{PiecewiseLinearBijectionNaive, Pivot, LinearTransform}
import oscar.cbls.invariants.core.algo.rb.{RBPosition, RedBlackTree}

object UniqueIntSequence{
  def apply(maxPivot:Int = 10, maxSize:Int = 1000):UniqueIntSequence = new UniqueIntSequence(
    RedBlackTree.empty[Int],
    RedBlackTree.empty[Int],
    PiecewiseLinearBijectionNaive.identity,
    0,
    maxPivot,
    maxSize
  )
}

class UniqueIntSequence(private[seq] val internalPositionToValue:RedBlackTree[Int],
                        private[seq] val valueToInternalPosition:RedBlackTree[Int],
                        private[seq] val externalToInternalPosition:PiecewiseLinearBijectionNaive,
                        private[seq] val startFreeRangeForInternalPosition:Int,
                        maxPivot:Int = 10, maxSize:Int = 1000) {


  def check {
    require(internalPositionToValue.content.sortBy(_._1) equals valueToInternalPosition.content.map({case (a,b) => (b,a)}).sortBy(_._1))
  }

  override def toString : String = {
    "(size:" + size + ")[" + this.iterator.toList.mkString(",") + "]" /* (\n" +
      "internalPositionToValue:" + internalPositionToValue.content +
      "\nvalueToInternalPosition:" + valueToInternalPosition.content +
      "\nexternalToInternalPosition:" + externalToInternalPosition + ")"*/
  }

  def size : Int = valueToInternalPosition.size

  def iterator : Iterator[Int] = new UniqueIntSequenceIterator(this)

  def valueAtPosition(position : Int) : Option[Int] = {
    val internalPosition:Int = externalToInternalPosition.forward(position)
    internalPositionToValue.get(internalPosition)
  }

  def positionOfValue(value : Int) : Option[Int] = {
    valueToInternalPosition.get(value) match{
      case None => None
      case Some(internalPosition) => Some(externalToInternalPosition.backward(internalPosition))
    }
  }

  def isValueIncluded(value:Int):Boolean = valueToInternalPosition.contains(value)

  def crawlerAtPosition(position : Int) : Option[IntSequenceExplorer] = {
    if (position >= this.size) None
    else {
      val currentPivotPosition = externalToInternalPosition.forward.pivotWithPositionApplyingTo(position)
      val (pivotAbovePosition,internalPosition) = currentPivotPosition match {
        case None => (None,position)
        case Some(p) => (p.next,p.value.f(position))
      }

      Some(new IntSequenceExplorer(this,
        position,
        internalPositionToValue.positionOf(internalPosition).head,
        currentPivotPosition,
        pivotAbovePosition)()
      )
    }
  }

  def crawlerAtValue(value: Int) : Option[IntSequenceExplorer] = {
    positionOfValue(value) match{
      case None => None
      case Some(position) => crawlerAtPosition(position)
    }
  }

  def insertAtPosition(value:Int, pos:Int):UniqueIntSequence = {
    println(this + ".insertAtPosition(value:" + value + " pos:" + pos + ")")
    require(pos<=size,"inserting past the end of the sequence (size:" + size + " pos:" + pos + ")")
    //insert into red blacks
    val newInternalPositionToValue = internalPositionToValue.insert(startFreeRangeForInternalPosition,value)
    val newValueToInternalPosition = valueToInternalPosition.insert(value,startFreeRangeForInternalPosition)

    //move sequence after position, one upward
    //move inserted point at its position
    val oldExternalPosRelatedToFreeInternalPos = externalToInternalPosition.backward(startFreeRangeForInternalPosition)

    val newExternalToInternalPosition = if(pos == size) {
      //inserting at end of the sequence
      externalToInternalPosition.updateBefore(
        (size,size,LinearTransform(oldExternalPosRelatedToFreeInternalPos-pos,false)))
    }else{
      //inserting somewhere within the sequence, need to shift upper part
      externalToInternalPosition.updateBefore(
        (pos+1,size,LinearTransform(-1,false)),
        (pos,pos,LinearTransform(oldExternalPosRelatedToFreeInternalPos-pos,false)))
    }

    new UniqueIntSequence(
      newInternalPositionToValue,
      newValueToInternalPosition,
      newExternalToInternalPosition,
      startFreeRangeForInternalPosition+1,
      maxPivot,maxSize)
  }

  def delete(pos:Int):UniqueIntSequence = {
    println(this + ".delete(pos:" + pos + ")")
    require(pos<size,"deleting past the end of the sequence (size:" + size + " pos:" + pos + ")")
    require(pos>=0,"deleting at negative pos:" + pos)

    val internalPosition = externalToInternalPosition(pos)
    val value = internalPositionToValue.get(internalPosition).head
    val largestInternalPosition = startFreeRangeForInternalPosition-1

    val valueAtLargestInternalPosition = internalPositionToValue.get(largestInternalPosition).head

    val newInternalPositionToValue = internalPositionToValue.
      insert(internalPosition,valueAtLargestInternalPosition).
      remove(largestInternalPosition)

    val newValueToInternalPosition = valueToInternalPosition.
      insert(valueAtLargestInternalPosition,internalPosition).
      remove(value)

    //now, update the fct knowing the move and remove
    val externalPositionAssociatedToLargestInternalPosition = externalToInternalPosition.backward(largestInternalPosition)

    val newExternalToInternalPosition = externalToInternalPosition.updateBefore(
      (externalPositionAssociatedToLargestInternalPosition,
        externalPositionAssociatedToLargestInternalPosition,
        LinearTransform(pos-externalPositionAssociatedToLargestInternalPosition,false)),
      (pos,pos,LinearTransform(externalPositionAssociatedToLargestInternalPosition - pos,false))).updateBefore(
      (pos,size-2,LinearTransform(1,false)),(size-1,size-1,LinearTransform(pos - size + 1,false)))

    new UniqueIntSequence(
      newInternalPositionToValue,
      newValueToInternalPosition,
      newExternalToInternalPosition,
      startFreeRangeForInternalPosition - 1,
      maxPivot,maxSize)
  }

  def moveAfter(startPositionIncluded:Int, endPositionIncluded:Int, moveAfterPosition:Int, flip:Boolean):UniqueIntSequence = {
    println(this + ".moveAfter(startPositionIncluded:" + startPositionIncluded + " endPositionIncluded:" + endPositionIncluded + " moveAfterPosition:" + moveAfterPosition + " flip:" + flip + ")")
    require(
      moveAfterPosition < startPositionIncluded || moveAfterPosition>endPositionIncluded,
      "moveAfterPosition=" +  moveAfterPosition + " cannot be between startPositionIncluded=" + startPositionIncluded + " and endPositionIncluded=" + endPositionIncluded)
    require(startPositionIncluded <= endPositionIncluded, "startPositionIncluded=" + startPositionIncluded + " should be <= endPositionIncluded=" + endPositionIncluded)

    if(moveAfterPosition + 1 == startPositionIncluded) {
      //not moving
      if(flip) { //just flipping
        val newExternalToInternalPosition = externalToInternalPosition.updateBefore(
          (startPositionIncluded,endPositionIncluded,LinearTransform(endPositionIncluded + startPositionIncluded,true)))

        new UniqueIntSequence(
          internalPositionToValue,
          valueToInternalPosition,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition,
          maxPivot,
          maxSize)

      }else{
        this //nop
      }
    }else{
      if(moveAfterPosition > startPositionIncluded){ //move upwards
        val newExternalToInternalPosition = externalToInternalPosition.updateBefore(
          (startPositionIncluded,
            moveAfterPosition + startPositionIncluded - endPositionIncluded -1,
            LinearTransform(endPositionIncluded + 1 - startPositionIncluded,false)),
          (startPositionIncluded + moveAfterPosition - endPositionIncluded,
            moveAfterPosition,
            LinearTransform((if(flip) (startPositionIncluded + moveAfterPosition)
            else (endPositionIncluded - moveAfterPosition)),flip)))

        new UniqueIntSequence(
          internalPositionToValue,
          valueToInternalPosition,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition,
          maxPivot,
          maxSize)

      }else{ //move downwards
        val newExternalToInternalPosition = externalToInternalPosition.updateBefore(
          (moveAfterPosition+1, moveAfterPosition + endPositionIncluded  - startPositionIncluded + 1,
            LinearTransform(if(flip) endPositionIncluded + moveAfterPosition + 1 else startPositionIncluded - moveAfterPosition - 1,flip)),
          (moveAfterPosition + endPositionIncluded  - startPositionIncluded + 2,endPositionIncluded,
            LinearTransform(startPositionIncluded - endPositionIncluded - 1,false)))

        new UniqueIntSequence(
          internalPositionToValue,
          valueToInternalPosition,
          newExternalToInternalPosition,
          startFreeRangeForInternalPosition,
          maxPivot,
          maxSize)
      }
    }
  }
  def regularize:UniqueIntSequence = {
    null
  }
}

class UniqueIntSequenceIterator(s:UniqueIntSequence) extends Iterator[Int] {
  var crawler = s.crawlerAtPosition(0)

  override def hasNext : Boolean =
    crawler match{
      case None => false
      case Some(_) => true}

  override def next() : Int = {
    val position = crawler.head
    crawler = position.next
    position.value
  }
}

class IntSequenceExplorer(sequence:UniqueIntSequence,
                          val position:Int,
                          positionInRB:RBPosition[Int],
                          currentPivotPosition:Option[RBPosition[Pivot]],
                          pivotAbovePosition:Option[RBPosition[Pivot]])(
                           limitAboveForCurrentPivot:Int = pivotAbovePosition match{
                             case None => sequence.externalToInternalPosition.forward.firstPivot.head._1
                             case Some(p) => p.value.fromValue-1},
                           limitBelowForCurrentPivot:Int = currentPivotPosition match{
                             case None => Int.MinValue
                             case Some(p) => p.value.fromValue},
                           slopeIsPositive:Boolean = currentPivotPosition match{
                             case None => true
                             case Some(p) => !p.value.f.minus}
                           ) {

  override def toString : String = "IntSequenceExplorer(position:" + position + " value:" + value + " positionInRB:" + positionInRB + ")"


  val value : Int = positionInRB.value

  // println("created " + this)

  def next : Option[IntSequenceExplorer] = {
    if(position == limitAboveForCurrentPivot){
      //change pivot, we are also sure that there is a next, so use .head

      val newPivotAbovePosition = pivotAbovePosition.head.next
      val newPosition = position + 1
      val newPositionInRBOpt = sequence.internalPositionToValue.positionOf(pivotAbovePosition.head.value.f(newPosition))
      newPositionInRBOpt match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new IntSequenceExplorer(sequence,
            newPosition,
            newPositionInRB,
            pivotAbovePosition,
            newPivotAbovePosition)(limitBelowForCurrentPivot = newPosition))
      }
    }else{
      //do not change pivot

      (if(slopeIsPositive) positionInRB.next else positionInRB.prev) match{
        case None => None
        case Some(newPositionInRB) =>
          Some(new IntSequenceExplorer(sequence,
            position + 1,
            newPositionInRB,
            currentPivotPosition,
            pivotAbovePosition)(
            limitAboveForCurrentPivot,
            limitBelowForCurrentPivot,
            slopeIsPositive))
      }
    }
  }

  def prev : Option[IntSequenceExplorer] = {
    if (position == 0) None
    else if (position  == limitBelowForCurrentPivot) {
      //change pivot

      val newPosition = position - 1
      val newCurrentPivotPosition = currentPivotPosition.head.prev
      val newInternalPosition = newCurrentPivotPosition.head.value.f(newPosition)
      val newCurrentPositionInRB = sequence.internalPositionToValue.positionOf(newInternalPosition).head
      //println("change pivot newPosition:" + newPosition + " newCurrentPivotPosition:" + newCurrentPivotPosition + " oldPosition:" + currentPivotPosition)
      Some(new IntSequenceExplorer(sequence,
        newPosition,
        newCurrentPositionInRB,
        newCurrentPivotPosition,
        currentPivotPosition)(limitAboveForCurrentPivot = limitBelowForCurrentPivot-1
      ))
    }else{
      //do not change pivot
      //println("not change pivot")
      Some(new IntSequenceExplorer(sequence,
        position-1,
        if(slopeIsPositive) positionInRB.prev.head else positionInRB.next.head,
        currentPivotPosition,
        pivotAbovePosition)(
        limitAboveForCurrentPivot,
        limitBelowForCurrentPivot,
        slopeIsPositive))
    }
  }
}
