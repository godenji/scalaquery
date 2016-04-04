package org.scalaquery

import java.sql.PreparedStatement
import org.scalaquery.session._
import org.scalaquery.util.CloseableIterator

/**
 * An invoker which executes an SQL statement.
 */
abstract class StatementInvoker[-P, +R] extends Invoker[P, R] { self =>

  protected def getStatement: String

  protected def setParam(param: P, st: PreparedStatement): Unit

  def elementsTo(param: P, maxRows: Int)(implicit session: Session): CloseableIterator[R] =
    results(param, maxRows).fold(r => new CloseableIterator.Single[R](r.asInstanceOf[R]), identity)

  /**
   * Invoke the statement and return the raw results.
   * TODO Support multiple results
   */
  def results(param: P, maxRows: Int,
    cursor: ResultSetType = ResultSetType.ForwardOnly,
    concurrency: ResultSetConcurrency = ResultSetConcurrency.ReadOnly,
    holdability: ResultSetHoldability = ResultSetHoldability.Default
  )
  (implicit session: Session): Either[Int, PositionedResultIterator[R]] = {
  	val currStatement = getStatement
    val statement = (
    	if(concurrency.intValue == ResultSetConcurrency.Updatable.intValue)
    		s"$currStatement FOR UPDATE"
    	else 
    		currStatement
    )
    val st = session.prepareStatement(
    	statement, cursor, concurrency, holdability
    )
    setParam(param, st)
    var doClose = true
    try {
      st.setMaxRows(maxRows)
      if(st.execute) {
        val rs = new PositionedResultIterator[R](st.getResultSet, maxRows) {
          def closeUnderlying() = st.close()
          def extractValue() = self.extractValue(this)
        }
        doClose = false
        Right(rs)
      } else Left(st.getUpdateCount)
    } finally if(doClose) st.close()
  }

  protected def extractValue(pr: PositionedResult): R
}
