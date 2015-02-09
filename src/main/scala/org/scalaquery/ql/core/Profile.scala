package org.scalaquery.ql.core

import org.scalaquery.ql.{
	Table, ColumnBase, Sequence, Query, Projection, DDL
}
import org.scalaquery.util.{
	ValueLinearizer, NamingContext, SQLBuilder
}

trait Profile {
  type ImplicitT <: ImplicitConversions[_ <: Profile]
  type TypeMapperDelegatesT <: TypeMapperDelegates

  def createQueryTemplate[P,R](query: Query[_,R]): 
  	QueryTemplate[P,R] = new QueryTemplate[P,R](query, this)
  	
  def createQueryBuilder(query: Query[_,_], nc: NamingContext): 
  	QueryBuilder = new ConcreteQueryBuilder(query, nc, None, this)

  val Implicit: ImplicitT
  val typeMapperDelegates: TypeMapperDelegatesT
  val sqlUtils = new SQLUtils

  def buildSelectStatement(query: Query[_,_], nc: NamingContext): 
  	(SQLBuilder.Result, ValueLinearizer[_]) = 
  		createQueryBuilder(query, nc).buildSelect
  		
  def buildUpdateStatement(query: Query[_,_], nc: NamingContext): 
  	SQLBuilder.Result = createQueryBuilder(query, nc).buildUpdate
  	
  def buildDeleteStatement(query: Query[_,_], nc: NamingContext): 
  	SQLBuilder.Result = createQueryBuilder(query, nc).buildDelete

  def buildInsertStatement(cb: Any): String = 
  	new InsertBuilder(cb, this).buildInsert
  	
  def buildInsertStatement(cb: Any, q: Query[_,_]): 
  	SQLBuilder.Result = new InsertBuilder(cb, this).buildInsert(q)

  def buildTableDDL(table: Table[_]): DDL = 
  	new DDLBuilder(table, this).buildDDL
  	
  def buildSequenceDDL(seq: Sequence[_]): DDL = 
  	new SequenceDDLBuilder(seq, this).buildDDL
}