package org.scalaquery.test

import org.junit.Test
import org.junit.Assert._
import org.scalaquery.ql._
import org.scalaquery.ql.TypeMapper._
import org.scalaquery.ql.extended.{ExtendedTable => Table}
import org.scalaquery.test.util._
import org.scalaquery.test.util.TestDB._
import org.scalaquery.session.Session

object CountTest extends DBTestObject(H2Mem, SQLiteMem, Postgres, MySQL, DerbyMem, HsqldbMem, SQLServer)

class CountTest(tdb: TestDB) extends DBTest(tdb) {
  import tdb.driver.Implicit._

  @Test def test() = db withSession { implicit ss:Session=>
    object TestTable extends Table[Int]("TEST") {
      def id = column[Int]("ID")
      def * = id
    }
    TestTable.ddl.create
    TestTable.insertAll(1, 2, 3, 4, 5)

    val q1 = Query(TestTable.count)
    q1.dump("q1: ")
    println("q1: "+q1.selectStatement)
    assertEquals(5, q1.first)

    val q2 = Query(Query(TestTable).count)
    q2.dump("q2: ")
    println("q2: "+q2.selectStatement)
    assertEquals(5, q2.first)

    val q3 = Query(TestTable.filter(_.id < 3).count)
    q3.dump("q3: ")
    println("q3: "+q3.selectStatement)
    assertEquals(2, q3.first)

    val q4 = Query(Query(TestTable).take(2).count)
    q4.dump("q4: ")
    println("q4: "+q4.selectStatement)
    assertEquals(2, q4.first)
  }
}
