package org.scalaquery.session

import java.util.Properties
import java.sql._
import javax.sql.DataSource
import javax.naming.InitialContext
import org.scalaquery.Fail

/**
 * A database instance to which connections can be created.
 * Encapsulates either a DataSource or parameters for DriverManager.getConnection().
 */
abstract class Database {

  protected[session] def createConnection(): Connection

  /**
   * The DatabaseCapabilities, accessed through a Session and created by the
   * first Session that needs them.
   */
  @volatile
  protected[session] var capabilities: DatabaseCapabilities = null

  /**
   * Create a new session. The session needs to be closed explicitly
   * by calling its close() method.
   */
  def createSession(): Session = new BaseSession(this)

  /**
   * Run the supplied function with a new session and automatically close
   * the session at the end.
   */
  def withSession[T](f: Session => T): T = {
    val s = createSession()
    try { f(s) } finally s.close()
  }

  /**
   * Run the supplied function with a new session in a transaction
   * and automatically close the session at the end.
   */
  def withTransaction[T](f: Session => T): T = withSession {
    s => s.withTransaction(f(s))
  }
}

/**
 * Factory methods for creating Database objects.
 */
object Database {

  /**
   * Create a Database based on a DataSource.
   */
  def forDataSource(ds: DataSource): Database = new Database {
    protected[session] def createConnection(): Connection = ds.getConnection
  }

  /**
   * Create a Database based on the JNDI name of a DataSource.
   */
  def forName(name: String) = new InitialContext().lookup(name) match {
    case ds: DataSource => forDataSource(ds)
    case x => Fail(
      s"Expected a DataSource for JNDI name $name, but got $x"
    )
  }

  /**
   * Create a Database that uses the DriverManager to open new connections.
   */
  def forURL(
    url: String,
    user: String = null,
    password: String = null,
    prop: Properties = null,
    driver: String = null
  ): Database = new Database {

    if (driver ne null) Class.forName(driver)
    val cprop = if (prop.ne(null) && user.eq(null) && password.eq(null)) prop else {
      val p = new Properties(prop)
      if (user ne null) p.setProperty("user", user)
      if (password ne null) p.setProperty("password", password)
      p
    }

    protected[session] def createConnection(): Connection =
      DriverManager.getConnection(url, cprop)
  }

  /**
   * Create a Database that uses the DriverManager to open new connections.
   */
  def forURL(url: String, prop: Map[String, String]): Database = {
    val p = new Properties
    if (prop ne null)
      for ((k, v) <- prop) if (k.ne(null) && v.ne(null)) p.setProperty(k, v)
    forURL(url, prop = p)
  }
}
