package org.scalaquery.ql.core

import org.scalaquery.ql.{Query, ColumnBase}
import org.scalaquery.session.{Session, PositionedParameters}
import org.scalaquery.util.NamingContext

final class UpdateInvoker[T](
	query: Query[_ <: ColumnBase[T], T], profile: Profile) {

  protected lazy val built = 
  	profile.buildUpdateStatement(query, NamingContext())

  def updateStatement = getStatement

  protected def getStatement = built.sql

  def update(value: T)(implicit session: Session): Int = 
  	session.withPreparedStatement(updateStatement) {st=>
	    st.clearParameters
	    val pp = new PositionedParameters(st)
	    query.unpackable.value.setParameter(profile, pp, Some(value))
	    built.setter(pp, null)
	    st.executeUpdate
	  }

  def updateInvoker: this.type = this
}
