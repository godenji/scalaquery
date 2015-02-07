package org.scalaquery.ql.core

import java.sql.Types._
import org.scalaquery.ql.TypeMapperDelegate

class SQLUtils {
  def quote(id: String): String = {
    val s = new StringBuilder(id.length + 4) append '"'
    for(c <- id) if(c == '"') s append "\"\"" else s append c
    s append '"' toString
  }

  def mapTypeName(tmd: TypeMapperDelegate[_]): String = 
  	tmd.sqlType match {
    	case VARCHAR => "VARCHAR(254)"
    	case _ => tmd.sqlTypeName
  	}
}
