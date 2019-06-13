package oscar.cbls.core.new_propagation

import oscar.cbls.algo.dag.DAGNode
import oscar.cbls.algo.dll.{DPFDLLStorageElement, DelayedPermaFilteredDoublyLinkedList}
import oscar.cbls.algo.quick.QList

abstract class PropagationElement() extends DAGNode {

  //var uniqueID:Int = -1 //DAG node already have this kind of stuff

  var isScheduled:Boolean = false

  private var mySchedulingHandler:SchedulingHandler = _ // null

  def schedulingHandler_=(sh:SchedulingHandler): Unit = {
    require(mySchedulingHandler == null)
    //it can only be assigned once;
    //do or do not do; there is no trial
    mySchedulingHandler = sh
  }

  def schedulingHandler:SchedulingHandler = mySchedulingHandler

  //this is the position used for propagation, used both within SCC and out of SCC
  //it is read by all runners
  var propagationPosition:Int = -1

  // //////////////////////////////////////////////////////////////////////
  //static propagation graph
  var staticallyListeningElements:QList[PropagationElement] = _ // null
  var staticallyListenedElements:QList[PropagationElement] = _ // null

  /**
    * listeningElement call this method to express that
    * they might register with dynamic dependency to the invocation target
    * @param listeningElement the PE that this is listening to
    */
  //TODO look whether this could be
  def registerStaticallyListeningElement(listeningElement: PropagationElement) {
    staticallyListeningElements = QList(listeningElement,staticallyListeningElements)
    listeningElement.staticallyListenedElements = QList(this,listeningElement.staticallyListenedElements)
  }

  // //////////////////////////////////////////////////////////////////////
  //dynamic propagation graph

  //the listening element call the listened element.
  //the listened element takes care of all internal stuff

  protected[this] val dynamicallyListeningElements: DelayedPermaFilteredDoublyLinkedList[(PropagationElement, Int)]
  = new DelayedPermaFilteredDoublyLinkedList[(PropagationElement, Int)]

  //temporary dynamic listening
  protected def registerTemporaryDynamicDependency(listeningElement:VaryingDependencies, id:Int): KeyForDynamicDependencyRemoval ={
    new KeyForDynamicDependencyRemoval(
      registerTemporaryDynamicDependencyListenedSide(listeningElement:VaryingDependencies,id:Int),
      listeningElement.registerTemporaryDynamicDependencyListeningSide(this))
  }

  //protected[propagation]
  protected def registerTemporaryDynamicDependencyListenedSide(listeningElement:VaryingDependencies,
                                                                            id:Int):  DPFDLLStorageElement[_] = {
    this.dynamicallyListeningElements.addElem((listeningElement,id))
  }


  //permanent dynamic listening
  protected def registerPermanentDynamicDependency(listeningElement:PropagationElement,id:Int): Unit ={
    registerPermanentDynamicDependencyListenedSide(listeningElement,id)
    listeningElement.registerPermanentDynamicDependencyListeningSide(this)
  }

  //protected[propagation]
  protected def registerPermanentDynamicDependencyListenedSide(listeningElement:PropagationElement ,
                                                                            id:Int) {
    this.dynamicallyListeningElements.addElem((listeningElement,id))
  }

  //protected[propagation]
  protected def registerPermanentDynamicDependencyListeningSide(listenedElement:PropagationElement): Unit ={
    // nothing to do here, we always listen to the same elements,
    // so our dependencies are captured by statically listened elements
    // dynamic dependencies PE will need to do something here, actually
    // and that's why there is this method
    // this call is not going to be performed many times because it is about permanent dependency,
    // so only called at startup and never during search
  }

  // //////////////////////////////////////////////////////////////////////
  //DAG stuff, for SCC sort

  //We have a getter because a specific setter is defined here below
  def scc:Option[StronglyConnectedComponent] = schedulingHandler match {
    case scc: StronglyConnectedComponent => Some(scc)
    case _ => None
  }

  def scc_=(scc:StronglyConnectedComponent): Unit = {
    require(this.schedulingHandler == null)
    schedulingHandler = scc
    initiateDAGSucceedingNodesAfterSCCDefinition(scc)
    initiateDAGPrecedingNodesAfterSCCDefinition(scc)
  }

  //override (?)
  def positionInTopologicalSort: Int = propagationPosition

  //override
  def positionInTopologicalSort_=(newValue: Int): Unit = {propagationPosition = newValue}

  def compare(that: DAGNode): Int = {
    assert(this.uniqueID != -1, "cannot compare non-registered PropagationElements this: [" + this + "] that: [" + that + "]")
    assert(that.uniqueID != -1, "cannot compare non-registered PropagationElements this: [" + this + "] that: [" + that + "]")
    this.uniqueID - that.uniqueID
  }

  final var getDAGPrecedingNodes: Iterable[DAGNode] = _ // null
  final var getDAGSucceedingNodes: Iterable[DAGNode] = _ // null

  private def initiateDAGSucceedingNodesAfterSCCDefinition(scc:StronglyConnectedComponent) {
    //we have to create the SCC injectors that will maintain the filtered Perma filter of nodes in the same SCC
    //for the listening side
    def filterForListening(listeningAndPayload: (PropagationElement, Int),
                           injector: () => Unit,
                           isStillValid: () => Boolean) {
      val listening = listeningAndPayload._1
      if (scc == listening.scc.get) {
        scc.registerOrCompleteWaitingDependency(this, listening, injector, isStillValid)
      }
    }
    getDAGSucceedingNodes = dynamicallyListeningElements.delayedPermaFilter(filterForListening, _._1)
  }

  protected def initiateDAGPrecedingNodesAfterSCCDefinition(scc:StronglyConnectedComponent){
    getDAGPrecedingNodes = staticallyListenedElements.filter(_.scc.get == scc)
  }

  // ////////////////////////////////////////////////////////////////////////
  // to spare on memory (since we have somewhat memory consuming PE
  def dropUselessGraphAfterClose(): Unit ={
    staticallyListenedElements = null
  }

  // ////////////////////////////////////////////////////////////////////////
  // api about scheduling and propagation

  protected def scheduleMyselfForPropagation(): Unit ={
    if(!isScheduled){
      isScheduled = true
      if(schedulingHandler != null){
        //at startup, SH are null
        schedulingHandler.schedulePEForPropagation(this)
      }
    }
  }

  def reScheduleIfScheduled(): Unit ={
    if(isScheduled){
      schedulingHandler.schedulePEForPropagation(this)
    }
  }

  def ensureUpToDate(){
    if(schedulingHandler != null){
      //at startup, SH are null
      schedulingHandler.ensureUpToDateStartPropagationIfNeeded()
    }
  }

  final def propagate(){
    require(isScheduled)
    isScheduled = false
    // perform propagation might reschedule the stuff, so we update isScheduled
    // before calling performPropagation
    performPropagation()
  }

  protected def performPropagation():Unit

  def checkInternals():Unit = throw new Error("checkInternals not implemented")
}

trait VaryingDependencies extends PropagationElement {

  //overrides on dynamic dependencies (permanent and temporary)
  var permanentListenedPE:QList[PropagationElement] = _ // null

  val dynamicallyListenedElements: DelayedPermaFilteredDoublyLinkedList[PropagationElement]
  = new DelayedPermaFilteredDoublyLinkedList[PropagationElement]

  //protected[propagation]
  override protected def registerPermanentDynamicDependencyListeningSide(listenedElement:PropagationElement){
    dynamicallyListenedElements.addElem(listenedElement)
    permanentListenedPE = QList(listenedElement,permanentListenedPE)
  }

  //protected[propagation]
  def registerTemporaryDynamicDependencyListeningSide(listenedElement:PropagationElement):DPFDLLStorageElement[_] = {
    dynamicallyListenedElements.addElem(listenedElement)
  }

  //added stuff for SCC management
  override protected def initiateDAGPrecedingNodesAfterSCCDefinition(scc:StronglyConnectedComponent){

    def filterForListened(listened: PropagationElement,
                          injector: () => Unit,
                          isStillValid: () => Boolean){
      listened.scc match{
        case Some(otherScc) if scc == otherScc =>
          scc.registerOrCompleteWaitingDependency(listened, this, injector, isStillValid)
        case _ => ;
      }
    }
    getDAGPrecedingNodes = dynamicallyListenedElements.delayedPermaFilter(filterForListened)
  }

  override def dropUselessGraphAfterClose(): Unit ={
    staticallyListenedElements = null
    staticallyListeningElements = null
  }
}

class CallBackPropagationElement(callBackTarget:()=>Unit,
                                 staticallyListeningElements:Iterable[PropagationElement],
                                 staticallyListenedElements:Iterable[PropagationElement])
  extends PropagationElement(){

  // We register this to be between the listened elements and the varying dependency PE
  // there is no dynamic dependency however because we do not want any notification
  // and because this PE will be scheduled by some external mechanism
  for(staticallyListened <- staticallyListenedElements){
    staticallyListened.registerStaticallyListeningElement(this)
  }
  for(staticallyListening <- staticallyListeningElements){
    this.registerStaticallyListeningElement(staticallyListening)
  }

  override protected def performPropagation(): Unit ={
    callBackTarget()
  }
}

/**
  * This class is used in as a handle to register and unregister dynamically to variables
  * @author renaud.delandtsheer@cetic.be
  */
class KeyForDynamicDependencyRemoval(key1: DPFDLLStorageElement[_],
                                     key2: DPFDLLStorageElement[_]) {
  def performRemove(): Unit = {
    key1.delete()
    key2.delete()
  }
}
