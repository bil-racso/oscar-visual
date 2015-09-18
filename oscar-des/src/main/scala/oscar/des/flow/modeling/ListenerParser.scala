package oscar.des.flow.modeling

import oscar.des.engine.Model
import oscar.des.flow.lib._

import scala.collection.immutable.SortedMap
import scala.util.parsing.combinator._

abstract class QuickParseResult(val parseOK:Boolean)
case class QuickParseError(s:String) extends QuickParseResult(false){
  override def toString: String = s
}
case object QuickParseOK extends QuickParseResult(true)

/**
 * This is a wrapper over the parser that can evaluate whether an
 * expression is syntactly correct or not, given a set of storages and processes.
 *
 * @param storages
 * @param processes
 */
class QuickParser(storages:Iterable[String],processes:Iterable[String]){
  val m = new Model
  val storagesMap = storages.foldLeft[SortedMap[String,Storage]](SortedMap.empty)(
    (theMap,storageName) => theMap + ((storageName,new FIFOStorage(10,Nil,storageName,null,false))))
  val processMap = processes.foldLeft[SortedMap[String,ActivableProcess]](SortedMap.empty)(
    (theMap,processName) => theMap + ((processName,new SingleBatchProcess(m, () => 1.0 , Array(), Array(), null, processName, null))))

  val myParser = new ListenerParser(storagesMap,processMap)

  def checkSyntax(expression:String):QuickParseResult = {
    myParser(expression) match{
      case ParsingError(s) => QuickParseError(s)
      case _ => QuickParseOK
    }
  }
}

sealed class ListenerParsingResult
case class DoubleExpressionResult(d:DoubleExpr) extends ListenerParsingResult
case class BooleanExpressionResult(b:BoolExpr) extends ListenerParsingResult
case class ParsingError(s:String) extends ListenerParsingResult {
  override def toString: String = "Parse Error:\n" + s + "\n"
}

object ListenerParser{
  def apply(storages:Iterable[Storage],processes:Iterable[ActivableProcess]): ListenerParser ={
    val storagesMap = storages.foldLeft[SortedMap[String,Storage]](SortedMap.empty)(
      (theMap,storage) => theMap + ((storage.name,storage)))
    val processMap = processes.foldLeft[SortedMap[String,ActivableProcess]](SortedMap.empty)(
      (theMap,process) => theMap + ((process.name,process)))

    new ListenerParser(storagesMap, processMap)
  }
}
/**
 * Created by rdl on 08-09-15.
 */
class ListenerParser(storages:Map[String,Storage],
                     processes:Map[String,ActivableProcess])
  extends ParserWithSymbolTable with ListenersHelper{

  protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/)+""".r

  override def skipWhitespace: Boolean = true

  def apply(input:String):ListenerParsingResult = {
    parseAll(expressionParser, input) match {
      case Success(result:BoolExpr, _) => BooleanExpressionResult(result)
      case Success(result:DoubleExpr, _) => DoubleExpressionResult(result)
      case n:NoSuccess => ParsingError(n.toString)
    }
  }

  def expressionParser:Parser[Expression] = doubleExprParser | boolExprParser

  def boolExprParser:Parser[BoolExpr] = (
    "[*]" ~> boolExprParser ^^ {case b => hasAlwaysBeen(b)}
      | "<*>" ~> boolExprParser ^^ {case b => hasBeen(b)}
      | "@T" ~> boolExprParser ^^ {case b => becomesTrue(b)}
      | "@F" ~> boolExprParser ^^ {case b => becomesFalse(b)}
      | doubleExprParser~(">="|">"|"<="|"<"|"!="|"=")~doubleExprParser ^^ {
      case (a~op~b) => op match{
        case ">" => g(a,b)
        case ">=" => ge(a,b)
        case "<" => l(a,b)
        case "<=" => le(a,b)
        case "=" => eq(a,b)
        case "!=" => neq(a,b)
      }}
    | disjunctionParser)

  def disjunctionParser:Parser[BoolExpr] =
    conjunctionParser ~ opt("|"~>disjunctionParser) ^^ {
      case a~None => a
      case a~Some(b) => or(a,b)}

  def conjunctionParser:Parser[BoolExpr]  =
    atomicBoolExprParser ~ opt("&"~>conjunctionParser) ^^ {
      case a~None => a
      case a~Some(b) => and(a,b)}

  def atomicBoolExprParser:Parser[BoolExpr] = (
    "empty(" ~> storageParser <~")" ^^ {empty(_)}
      | processBoolProbe("running",running)
      | processBoolProbe("anyBatchStarted",anyBatchStarted)
      | "true" ^^^ boolConst(true)
      | "false" ^^^ boolConst(false)
      | binaryOperatorBB2BParser("and",and)
      | binaryOperatorBB2BParser("or",or)
      | binaryOperatorBB2BParser("since",since)
      | unaryOperatorB2BParser("not",not)
      | "!"~>boolExprParser^^{case (b:BoolExpr) => not(b)}
      | unaryOperatorB2BParser("hasAlwaysBeen",hasAlwaysBeen)
      | unaryOperatorB2BParser("hasBeen",hasBeen)
      | unaryOperatorB2BParser("becomesTrue",becomesTrue)
      | unaryOperatorB2BParser("becomesFalse",becomesFalse)
      | binaryOperatorDD2BParser("g",g)
      | binaryOperatorDD2BParser("ge",ge)
      | binaryOperatorDD2BParser("l",l)
      | binaryOperatorDD2BParser("le",le)
      | binaryOperatorDD2BParser("eq",eq)
      | binaryOperatorDD2BParser("ne",neq)
      | "changed(" ~> (boolExprParser | doubleExprParser) <~")" ^^ {case e:Expression => changed(e)}
      | "("~>boolExprParser<~")"
      | failure("expected boolean expression"))

  def binaryTerm:Parser[BoolExpr] = unaryOperatorB2BParser("not",not)

  def doubleExprParser:Parser[DoubleExpr] =
    term ~ opt(("+"|"-")~doubleExprParser) ^^ {
      case a~None => a
      case a~Some("+"~b) => plus(a,b)
      case a~Some("-"~b) => minus(a,b)}

  def term: Parser[DoubleExpr] =
    atomicDoubleExprParser ~ opt(("*"|"/")~term) ^^ {
      case a~None => a
      case a~Some("*"~b) => mult(a,b)
      case a~Some("/"~b) => div(a,b)}

  def atomicDoubleExprParser:Parser[DoubleExpr] = (
    storageDoubleProbe("stockLevel",stockLevel)
      | storageDoubleProbe("stockCapacity",stockCapacity)
      | storageDoubleProbe("relativeStockLevel",relativeStockLevel)
      | storageDoubleProbe("totalPut",totalPut)
      | storageDoubleProbe("totalFetch",totalFetch)
      | storageDoubleProbe("totalLosByOverflow",totalLosByOverflow)
      | processDoubleProbe("completedBatchCount",completedBatchCount)
      | processDoubleProbe("startedBatchCount",startedBatchCount)
      | processDoubleProbe("totalWaitDuration",totalWaitDuration)
      | doubleParser ^^ {d:Double => doubleConst(d)}
      | binaryOperatorDD2DParser("plus",plus)
      | binaryOperatorDD2DParser("minus",minus)
      | binaryOperatorDD2DParser("mult",mult)
      | binaryOperatorDD2DParser("div",(a,b) => div(a,b))
      | unaryOperatorD2DParser("opposite",opposite)
      | unaryOperatorD2DParser("delta",delta)
      | unaryOperatorB2DParser("cumulatedDuration",cumulatedDuration)
      | unaryOperatorB2DParser("cumulatedDurationNotStart",culumatedDurationNotStart)
      | "time"^^^ currentTime
      | "tic" ^^^ delta(currentTime)
      | unaryOperatorD2DParser("ponderateWithDuration",ponderateWithDuration)
      | ("maxOnHistory("|"max(") ~> doubleExprParser~opt("," ~> boolExprParser)<~")" ^^ {
      case (d~None) => maxOnHistory(d)
      case (d~Some(cond:BoolExpr)) => maxOnHistory(d,cond)}
      | ("minOnHistory("|"min(") ~> doubleExprParser~opt("," ~> boolExprParser)<~")"^^ {
      case (d~None) => minOnHistory(d)
      case (d~Some(cond:BoolExpr)) => minOnHistory(d,cond)}
      | unaryOperatorD2DParser("avg",avgOnHistory)
      | unaryOperatorD2DParser("avgOnHistory",avgOnHistory)
      | "-"~> doubleExprParser ^^ {opposite(_)}
      | "("~>doubleExprParser<~")")

  //generic code

  //probes on storages
  def storageDoubleProbe(probeName:String,constructor:Storage=>DoubleExpr):Parser[DoubleExpr] =
    probeName~>"("~>storageParser <~")" ^^ {constructor(_)}

  def storageParser:Parser[Storage] = identifier convertStringUsingSymbolTable(storages, "storage")

  //probes on processes
  def processDoubleProbe(probeName:String,constructor:ActivableProcess=>DoubleExpr):Parser[DoubleExpr] =
    probeName~>"("~>processParser <~")" ^^ {constructor(_)}
  def processBoolProbe(probeName:String,constructor:ActivableProcess=>BoolExpr):Parser[BoolExpr] =
    probeName~>"("~>processParser <~")" ^^ {constructor(_)}
  def processParser:Parser[ActivableProcess] = identifier convertStringUsingSymbolTable(processes, "process")

  // some generic parsing methods
  def unaryOperatorD2DParser(operatorString:String,constructor:DoubleExpr=>DoubleExpr):Parser[DoubleExpr] =
    operatorString~>"("~>doubleExprParser<~")" ^^ {
      case param => constructor(param)
    }

  def unaryOperatorB2BParser(operatorString:String,constructor:BoolExpr=>BoolExpr):Parser[BoolExpr] =
    operatorString~>"("~>boolExprParser<~")" ^^ {
      case param => constructor(param)
    }

  def unaryOperatorB2DParser(operatorString:String,constructor:BoolExpr=>DoubleExpr):Parser[DoubleExpr] =
    operatorString~>"("~>boolExprParser<~")" ^^ {
      case param => constructor(param)
    }

  def binaryOperatorDD2DParser(operatorString:String,constructor:(DoubleExpr,DoubleExpr)=>DoubleExpr):Parser[DoubleExpr] =
    operatorString~"("~>doubleExprParser~(","~>doubleExprParser<~")") ^^ {
      case param1~param2 => constructor(param1,param2)
    }

  def binaryOperatorDD2BParser(operatorString:String,constructor:(DoubleExpr,DoubleExpr)=>BoolExpr):Parser[BoolExpr] =
    operatorString~>"("~>doubleExprParser~(","~>doubleExprParser<~")") ^^ {
      case param1~param2 => constructor(param1,param2)
    }

  def binaryOperatorBB2BParser(operatorString:String,constructor:(BoolExpr,BoolExpr)=>BoolExpr):Parser[BoolExpr] =
    operatorString~>"("~>boolExprParser~(","~>boolExprParser<~")") ^^ {
      case param1~param2 => constructor(param1,param2)
    }


  def identifier:Parser[String] = """[a-zA-Z0-9]+""".r ^^ {_.toString}

  def doubleParser:Parser[Double] = """[0-9]+(\.[0-9]+)?""".r ^^ {case s:String => println("converting" + s);s.toDouble}
}

object ParserTester extends App with FactoryHelper{

  val m = new Model
  val aStorage = new FIFOStorage(10,Nil,"aStorage",null,false)
  val bStorage = new FIFOStorage(10,Nil,"bStorage",null,false)

  val aProcess = new SingleBatchProcess(m, 5000, Array(), Array((()=>1,aStorage)), null, "aProcess", null)
  val bProcess = new SingleBatchProcess(m, 5000, Array(), Array((()=>1,aStorage)), null, "bProcess", null)

  val myParser = ListenerParser(List(aStorage,bStorage), List(aProcess,bProcess))

  def testOn(s:String){
    println("testing on:" + s)
    println(myParser(s))
    println
  }

  testOn("completedBatchCount(aProcess) /*a comment in the middle*/ * totalPut(aStorage)")
  testOn("-(-(-completedBatchCount(aProcess)) * -totalPut(aStorage))")
  testOn("-(-(-completedBatchCount(aProcess)) + -totalPut(aStorage))")
  testOn("cumulatedDuration(empty(bStorage))")
  testOn("cumulatedDuration(!!!<*>running(bProcess))")
  testOn("cumulatedDuration(!!!<*>running(cProcess))")
  testOn("empty(aStorage) & empty(aStorage) | empty(aStorage)")
  testOn("cumulatedDuration(!running(bProcess))")
  testOn("cumulatedDurationNotStart(not(running(aProcess)))")
  testOn("max(stockLevel(aStorage))")
  testOn("min(stockLevel(aStorage))")
  testOn("avg(relativeStockLevel(bStorage))")
  testOn("avg(stockLevel(aStorage))")
  testOn("ponderateWithDuration(stockLevel(bStorage))")
}

trait ParserWithSymbolTable extends RegexParsers{
  class parserWithSymbolTable(identifierParser: Parser[String]) {
    def convertStringUsingSymbolTable[U](symbolTable: Map[String,U], symbolType: String): Parser[U] = new Parser[U] {
      def apply(in: Input) = identifierParser(in) match {
        case Success(x, in1) => symbolTable.get(x) match {
          case Some(u: U) => Success(u, in1)
          case None => Failure("" + x + " is not a known " + symbolType + ": (" + symbolTable.keys.mkString(",") + ")", in)
        }
        case f: Failure => f
        case e: Error => e
      }
    }
  }

  implicit def addSymbolTableFeature(identifierParser:Parser[String]):parserWithSymbolTable = new parserWithSymbolTable(identifierParser)
}