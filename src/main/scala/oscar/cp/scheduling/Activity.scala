/*******************************************************************************
 * This file is part of OscaR (Scala in OR).
 *   
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *  
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/gpl-3.0.html
 ******************************************************************************/
package oscar.cp.scheduling;

import oscar.cp.core.CPVarInt;
import oscar.cp.core.Store;
import oscar.cp.constraints.LeEq
import oscar.cp.modeling.CPScheduler

class Activity(val scheduler : CPScheduler, startVar: CPVarInt, durVar: CPVarInt, endVar: CPVarInt) {
    
	// Linking the variables
	scheduler.add(startVar + durVar == endVar) 
	// Link the resource to the scheduler and get an id
	val id = scheduler.addActivity(this)

	// The variables
    def start = startVar
    def end = endVar
    def dur = durVar
    
	/** earliest starting time
	 */
	def est = start.min
	
	/** latest starting time
	 */
	def lst = start.max
	
	/** earliest completion time assuming the smallest duration
	 */
	def ect = end.min
	
	/** latest completion time assuming the smallest duration
	 */
	def lct = end.max
	
	/** current minimal duration of this activity
	 */
	def minDuration = dur.min

	/** current maximal duration of this activity
	 */
	def maxDuration = dur.max
	
	def store = scheduler
	
	override def toString = "dur: "+dur+ " in ["+est+","+lct+"["
	
	def adjustStart(v : Int) = start.updateMin(v)	

	// Precedences 
	// -----------------------------------------------------------
	
	def precedes(act : Activity) = endBeforeStart(act)
	
	def endBeforeEnd(act : Activity) = new ActivityPrecedence(this, act, EBE)
	def endBeforeStart(act : Activity) = new ActivityPrecedence(this, act, EBS)
	def startBeforeEnd(act : Activity) = new ActivityPrecedence(this, act, SBE)
	def startBeforeStart(act : Activity) = new ActivityPrecedence(this, act, SBS)
	
	def endAtEnd(act : Activity) = new ActivityPrecedence(this, act, EAE)
	def endAtStart(act : Activity) = new ActivityPrecedence(this, act, EAS)
	def startAtEnd(act : Activity) = new ActivityPrecedence(this, act, SAE)
	def startAtStart(act : Activity) = new ActivityPrecedence(this, act, SAS)

	// Needs / Gives
	// -----------------------------------------------------------
	
	// UnitResource
	def needs(resource : UnitResource) {
    	resource.addActivity(this)
    }
	
	// CumulativeResource
	def needs(resource : CumulativeResource, capacity : Capacity) {
		resource.addActivity(this, capacity.variable(scheduler))
	}
	
	def needsF(resource : CumulativeResource, capacity : Capacity, atEnd : Boolean = true) {
		resource.addProdConsActivity(this, capacity.variable(scheduler), atEnd)
    }
	
	def gives(resource : CumulativeResource, capacity : Capacity) {
		resource.addActivity(this, capacity.opposite(scheduler))
	}
	
	def givesF(resource : CumulativeResource, capacity : Capacity, atEnd : Boolean = true) {
    	resource.addProdConsActivity(this, capacity.opposite(scheduler), atEnd)
    }
	
	/*def needs(resource : CumulativeResource, capacity : Int) {
    	assert(capacity >= 0)
		resource.addActivity(this, capacity)
    }
	
	def needs(resource : CumulativeResource, capacity : Range) {
    	assert(capacity.min >= 0)
		resource.addActivity(this, capacity)
	}
	
	def supplies(resource : CumulativeResource, capacity : Int) {
    	assert(capacity >= 0)
    	resource.addActivity(this, -capacity)
    }
	
	def supplies(resource : CumulativeResource, capacity : Range) {
    	assert(capacity.min >= 0)
		resource.addActivity(this, -capacity.max to -capacity.min)
	}
	
	def needsForever(resource : CumulativeResource, capacity : Int, atEnd : Boolean = true) {
    	assert(capacity >= 0)
		resource.addProdConsActivity(this, capacity, atEnd)
    }
	
	def suppliesForever(resource : CumulativeResource, capacity : Int, atEnd : Boolean = true) {
    	assert(capacity >= 0)
		resource.addProdConsActivity(this, -capacity, atEnd)
    }*/
	
	// CumulativeResourceSet	
	def needs(resource : CumulativeResourceSet, resources : Array[Int], capacity : Range) {
		assert(capacity.min >= 0)
		resource.addActivity(this, resources, capacity)	
	}
	
	def needs(resource : CumulativeResourceSet, resources : Array[Int], capacity : Int) {
		assert(capacity >= 0)
		resource.addActivity(this, resources, capacity)	
	}
	
	def supplies(resource : CumulativeResourceSet, resources : Array[Int], capacity : Range) {
		assert(capacity.min >= 0)
		resource.addActivity(this, resources, -capacity.max to -capacity.min)	
	}
	
	def supplies(resource : CumulativeResourceSet, resources : Array[Int], capacity : Int) {
		assert(capacity >= 0)
		resource.addActivity(this, resources, -capacity)	
	}
}

object Activity {
	
	def apply(scheduler : CPScheduler, dur : Int) = build(scheduler, CPVarInt(scheduler, dur))
	
	def apply(scheduler : CPScheduler, dur : Range) = build(scheduler, CPVarInt(scheduler, dur))

	def apply(scheduler : CPScheduler, durVar : CPVarInt) = build(scheduler, durVar)
	
	private def build(scheduler : CPScheduler, durVar : CPVarInt) : Activity = {
		
		val startVar : CPVarInt = CPVarInt(scheduler, 0 to scheduler.horizon - durVar.min)
		val endVar   : CPVarInt = CPVarInt(scheduler, durVar.min to scheduler.horizon) 
		return new Activity(scheduler, startVar, durVar, endVar)
    }
}



class MirrorActivity(val act: Activity)  extends Activity(act.scheduler, act.start, act.dur, act.end) {

	override def start: CPVarInt = throw new UninitializedFieldError("not available") 
	
	override def end: CPVarInt = throw new UninitializedFieldError("not available") 
	
	/**
	 * earliest starting time
	 */
	override def est = - act.lct;
	
	/**
	 * latest starting time
	 */
	override def lst = - act.ect;
	
	/**
	 * earliest completion time assuming the smallest duration
	 */
	override def ect = - act.lst

	/**
	 * latest completion time assuming the smallest duration
	 */
	override def lct = - act.est
	
	override def adjustStart(v : Int) = end.updateMax(-v)

	override def toString() = "mirror of activity:"+act;
	
	override def endBeforeEnd(act : Activity)     = throw new UninitializedFieldError("not available") 
	override def endBeforeStart(act : Activity)   = throw new UninitializedFieldError("not available") 
	override def startBeforeEnd(act : Activity)   = throw new UninitializedFieldError("not available") 
	override def startBeforeStart(act : Activity) = throw new UninitializedFieldError("not available") 
	
	override def endAtEnd(act : Activity)         = throw new UninitializedFieldError("not available") 
	override def endAtStart(act : Activity)       = throw new UninitializedFieldError("not available") 
	override def startAtEnd(act : Activity)       = throw new UninitializedFieldError("not available") 
	override def startAtStart(act : Activity)     = throw new UninitializedFieldError("not available") 
}
