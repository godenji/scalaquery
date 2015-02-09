package org.scalaquery.ql.driver

import org.scalaquery.SQueryException
import org.scalaquery.ql._
import org.scalaquery.ql.core._
import org.scalaquery.util._

class MySQLDriver extends Profile { self =>

  type ImplicitT = ImplicitConversions[MySQLDriver]
  type TypeMapperDelegatesT = MySQLTypeMapperDelegates

  val Implicit = new ImplicitConversions[MySQLDriver] {
    implicit val scalaQueryDriver = self
  }

  val typeMapperDelegates = new MySQLTypeMapperDelegates
  override val sqlUtils = new MySQLSQLUtils

  override def createQueryBuilder(query: Query[_, _], nc: NamingContext) = new MySQLQueryBuilder(query, nc, None, this)
  override def buildTableDDL(table: Table[_]): DDL = new MySQLDDLBuilder(table, this).buildDDL
  override def buildSequenceDDL(seq: Sequence[_]): DDL = new MySQLSequenceDDLBuilder(seq, this).buildDDL
}

object MySQLDriver extends MySQLDriver

class MySQLTypeMapperDelegates extends TypeMapperDelegates {
  override val stringTypeMapperDelegate = new TypeMapperDelegates.StringTypeMapperDelegate {
    override def value2SQLLiteral(value: String) = if(value eq null) "NULL" else {
      val sb = new StringBuilder
      sb append '\''
      for(c <- value) c match {
        case '\'' => sb append "\\'"
        case '"' => sb append "\\\""
        case 0 => sb append "\\0"
        case 26 => sb append "\\Z"
        case '\b' => sb append "\\b"
        case '\n' => sb append "\\n"
        case '\r' => sb append "\\r"
        case '\t' => sb append "\\t"
        case '\\' => sb append "\\\\"
        case _ => sb append c
      }
      sb append '\''
      sb.toString
    }
  }

  override val uuidTypeMapperDelegate = new TypeMapperDelegates.UUIDTypeMapperDelegate {
    override def sqlType = java.sql.Types.BINARY
    override def sqlTypeName = "BINARY(16)"
  }
}

class MySQLQueryBuilder(_query: Query[_, _], _nc: NamingContext, parent: Option[QueryBuilder], profile: MySQLDriver)
extends QueryBuilder(_query, _nc, parent, profile) {

  import profile.sqlUtils._

  override type Self = MySQLQueryBuilder
  override protected val scalarFrom = Some("DUAL")
  override protected val supportsCast = false

  protected def createSubQueryBuilder(query: Query[_, _], nc: NamingContext) =
    new MySQLQueryBuilder(query, nc, Some(this), profile)

  override protected def innerExpr(c: Node, b: SQLBuilder): Unit = c match {
    case EscFunction("concat", l, r) => b += "concat("; expr(l, b); b += ','; expr(r, b); b += ')'
    case Sequence.Nextval(seq) => b += s"${quote(seq.name + "_nextval")}()"
    case Sequence.Currval(seq) => b += s"${quote(seq.name + "_currval")}()"
    case _ => super.innerExpr(c, b)
  }
  
  override protected def appendLimitClause(b: SQLBuilder) = query.typedModifiers[TakeDrop].lastOption.foreach {
  	case TakeDrop(Some(t), Some(d), compareNode) => 
  		appendColumnValue(b+=" LIMIT ", d, compareNode); appendColumnValue(b+=",",t)
  		
    case TakeDrop(Some(t), None, _) => appendColumnValue(b+=" LIMIT ",t)
    case TakeDrop(None, Some(d), _) => appendColumnValue(b+=" LIMIT ",d); b+= ",18446744073709551615"
    case _ =>
  }

  override protected def appendOrdering(o: Ordering, b: SQLBuilder) {
    val desc = o.isInstanceOf[Ordering.Desc]
    if(o.nullOrdering == Ordering.NullsLast && !desc) {
      b += "isnull("
      expr(o.by, b)
      b += "),"
    } else if(o.nullOrdering == Ordering.NullsFirst && desc) {
      b += "isnull("
      expr(o.by, b)
      b += ") desc,"
    }
    expr(o.by, b)
    if(desc) b += " desc"
  }
}

class MySQLDDLBuilder(table: Table[_], profile: MySQLDriver) extends DDLBuilder(table, profile) {
  override protected def dropForeignKey(fk: ForeignKey[_ <: Table[_], _]) = {
    "ALTER TABLE " + table.tableName + " DROP FOREIGN KEY " + fk.name
  }
}

class MySQLSequenceDDLBuilder[T](seq: Sequence[T], profile: MySQLDriver) extends SequenceDDLBuilder(seq, profile) {
  import profile.sqlUtils._

  override def buildDDL: DDL = {
    import seq.integral._
    val sqlType = seq.typeMapper(profile).sqlTypeName
    val t = sqlType + " not null"
    val increment = seq._increment.getOrElse(one)
    val desc = increment < zero
    val minValue = seq._minValue getOrElse (if(desc) fromInt(java.lang.Integer.MIN_VALUE) else one)
    val maxValue = seq._maxValue getOrElse (if(desc) fromInt(-1) else fromInt(java.lang.Integer.MAX_VALUE))
    val start = seq._start.getOrElse(if(desc) maxValue else minValue)
    val beforeStart = start - increment
    if(!seq._cycle && (seq._minValue.isDefined && desc || seq._maxValue.isDefined && !desc))
      throw new SQueryException("Sequences with limited size and without CYCLE are not supported by MySQLDriver's sequence emulation")
    val incExpr = if(seq._cycle) {
      if(desc) "if(id-"+(-increment)+"<"+minValue+","+maxValue+",id-"+(-increment)+")"
      else "if(id+"+increment+">"+maxValue+","+minValue+",id+"+increment+")"
    } else {
      "id+("+increment+")"
    }
    //TODO Implement currval function
    new DDL {
      val createPhase1 = Iterable(
        "create table " + quote(seq.name + "_seq") + " (id " + t + ")",
        "insert into " + quote(seq.name + "_seq") + " values (" + beforeStart + ")",
        "create function " + quote(seq.name + "_nextval") + "() returns " + sqlType + " begin update " +
          quote(seq.name + "_seq") + " set id=last_insert_id(" + incExpr + "); return last_insert_id(); end")
      val createPhase2 = Nil
      val dropPhase1 = Nil
      val dropPhase2 = Iterable(
        "drop function " + quote(seq.name + "_nextval"),
        "drop table " + quote(seq.name + "_seq"))
    }
  }
}

class MySQLSQLUtils extends SQLUtils {
  override def quote(id: String) = '`' + id + '`'
}