package sangria.marshalling

import play.api.libs.json._

import scala.util.Try

object playJson extends PlayJsonSupportLowPrioImplicits {
  implicit object PlayJsonResultMarshaller extends ResultMarshaller {
    type Node = JsValue

    def emptyMapNode = JsObject(Seq.empty)
    def mapNode(keyValues: Seq[(String, JsValue)]) = JsObject(keyValues)
    def addMapNodeElem(node: JsValue, key: String, value: JsValue, optional: Boolean) =
      node.asInstanceOf[JsObject] + (key → value)

    def arrayNode(values: Vector[JsValue]) = JsArray(values)

    def optionalArrayNodeValue(value: Option[JsValue]) = value match {
      case Some(v) ⇒ v
      case None ⇒ nullNode
    }

    def stringNode(value: String) = JsString(value)
    def floatNode(value: Double) = JsNumber(value)
    def booleanNode(value: Boolean) = JsBoolean(value)
    def intNode(value: Int) = JsNumber(value)
    def bigIntNode(value: BigInt) = JsNumber(BigDecimal(value))
    def bigDecimalNode(value: BigDecimal) = JsNumber(value)

    def nullNode = JsNull

    def renderCompact(node: JsValue) = Json.stringify(node)
    def renderPretty(node: JsValue) = Json.prettyPrint(node)
  }

  implicit object PlayJsonMarshallerForType extends ResultMarshallerForType[JsValue] {
    val marshaller = PlayJsonResultMarshaller
  }

  implicit object PlayJsonInputUnmarshaller extends InputUnmarshaller[JsValue] {
    def getRootMapValue(node: JsValue, key: String) = node.asInstanceOf[JsObject].value get key

    def isListNode(node: JsValue) = node.isInstanceOf[JsArray]
    def getListValue(node: JsValue) = node.asInstanceOf[JsArray].value

    def isMapNode(node: JsValue) = node.isInstanceOf[JsObject]
    def getMapValue(node: JsValue, key: String) = node.asInstanceOf[JsObject].value get key
    def getMapKeys(node: JsValue) = node.asInstanceOf[JsObject].keys

    def isDefined(node: JsValue) = node != JsNull
    def getScalarValue(node: JsValue) = node match {
      case JsBoolean(b) ⇒ b
      case JsNumber(d) ⇒ d.toBigIntExact getOrElse d
      case JsString(s) ⇒ s
      case _ ⇒ throw new IllegalStateException(s"$node is not a scalar value")
    }

    def getScalaScalarValue(node: JsValue) = getScalarValue(node)

    def isEnumNode(node: JsValue) = node.isInstanceOf[JsString]

    def isScalarNode(node: JsValue) = node match {
      case _: JsBoolean | _: JsNumber | _: JsString ⇒ true
      case _ ⇒ false
    }

    def isVariableNode(node: JsValue) = false
    def getVariableName(node: JsValue) = throw new IllegalArgumentException("variables are not supported")

    def render(node: JsValue) = Json.stringify(node)
  }

  private object PlayJsonToInput extends ToInput[JsValue, JsValue] {
    def toInput(value: JsValue) = (value, PlayJsonInputUnmarshaller)
  }

  implicit def playJsonToInput[T <: JsValue]: ToInput[T, JsValue] =
    PlayJsonToInput.asInstanceOf[ToInput[T, JsValue]]

  implicit def playJsonWriterToInput[T : Writes]: ToInput[T, JsValue] =
    new ToInput[T, JsValue] {
      def toInput(value: T) = implicitly[Writes[T]].writes(value) → PlayJsonInputUnmarshaller
    }

  private object PlayJsonFromInput extends FromInput[JsValue] {
    val marshaller = PlayJsonResultMarshaller
    def fromResult(node: marshaller.Node) = node
  }

  implicit def playJsonFromInput[T <: JsValue]: FromInput[T] =
    PlayJsonFromInput.asInstanceOf[FromInput[T]]

  implicit def playJsonReaderFromInput[T : Reads]: FromInput[T] =
    new FromInput[T] {
      val marshaller = PlayJsonResultMarshaller
      def fromResult(node: marshaller.Node) = implicitly[Reads[T]].reads(node) match {
        case JsSuccess(v, _) ⇒ v
        case JsError(errors) ⇒
          val formattedErrors = errors.toVector.flatMap {
            case (JsPath(nodes), es) ⇒ es.map(e ⇒ s"At path '${nodes mkString "."}': ${e.message}")
          }

          throw InputParsingError(formattedErrors)
      }
    }

  implicit object PlayJsonInputParser extends InputParser[JsValue] {
    def parse(str: String) = Try(Json.parse(str))
  }
}

trait PlayJsonSupportLowPrioImplicits {
  implicit val PlayJsonInputUnmarshallerJObject =
    playJson.PlayJsonInputUnmarshaller.asInstanceOf[InputUnmarshaller[JsObject]]
}
