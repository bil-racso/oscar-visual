package oscar.cbls.algo.graph

/**
  * this is a FloydWarshall algorithm for the [[ConditionalGraph]] data structure.
  * this data structure is non-directed graph, the FloydWarshall is tehrefore tuned accordignly
  */
object FloydWarshall{

  def buildDistanceMatrix(g:ConditionalGraph,
                          isConditionalEdgeOpen:Int => Boolean):Array[Array[Long]] = {
    val m = buildAdjacencyMatrix(g:ConditionalGraph,
      isConditionalEdgeOpen:Int => Boolean)
    saturateAdjacencyMatrixToDistanceMatrix(m)
    m
  }

  def buildAdjacencyMatrix(g:ConditionalGraph,
                           isConditionalEdgeOpen:Int => Boolean):Array[Array[Long]] = {

    def isEdgeOpen(edge: Edge): Boolean =
      edge.conditionID match {
        case None => true
        case Some(condition) => isConditionalEdgeOpen(condition)
      }

    val n = g.nbNodes
    val distanceMatrix:Array[Array[Long]] = Array.tabulate(n)(_ => Array.fill(n)(Long.MaxValue))

    for(node <- g.nodes.indices){
      distanceMatrix(node)(node) = 0
    }

    for(edge <- g.edges if isEdgeOpen(edge)){
      val sl = edge.length min distanceMatrix(edge.nodeA.nodeId)(edge.nodeB.nodeId)
      distanceMatrix(edge.nodeA.nodeId)(edge.nodeB.nodeId) = sl
      distanceMatrix(edge.nodeB.nodeId)(edge.nodeA.nodeId) = sl
    }

    distanceMatrix
  }

  def buildAdjacencyHalfMatrix(g:ConditionalGraph,
                               isConditionalEdgeOpen:Int => Boolean):Array[Array[Long]] = {

    def isEdgeOpen(edge: Edge): Boolean =
      edge.conditionID match {
        case None => true
        case Some(condition) => isConditionalEdgeOpen(condition)
      }

    val n = g.nbNodes
    val matrix:Array[Array[Long]] = Array.tabulate(n)(n2 => Array.fill(n2+1)(Long.MaxValue))

    for(node <- g.nodes.indices){
      matrix(node)(node) = 0
    }

    for(edge <- g.edges if isEdgeOpen(edge)){

      val idA = edge.nodeA.nodeId
      val idB = edge.nodeB.nodeId

      val (minNode,maxNode) = if(idA>idB) (idB,idA) else (idA,idB)

      val sl = edge.length min matrix(maxNode)(minNode)
      matrix(maxNode)(minNode) = sl
    }

    matrix
  }

  def saturateAdjacencyMatrixToDistanceMatrix(w:Array[Array[Long]]){
    val n = w.length

    for (k <- 0 to n-1) {
      for (i <- (0 to n-1).par) {
        for (j <- i+1 to n-1) {

          if(w(i)(k) != Long.MaxValue && w(k)(j)!= Long.MaxValue) {
            val newDistance = w(i)(k) + w(k)(j)
            if (newDistance < w(i)(j)) {
              w(i)(j) = newDistance
              w(j)(i) = newDistance
            }
          }
        }
      }
    }
  }
}
