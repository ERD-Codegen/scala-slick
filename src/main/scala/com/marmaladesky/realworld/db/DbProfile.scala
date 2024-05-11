package com.marmaladesky.realworld.db

import com.github.tminglei.slickpg._

import java.sql.{JDBCType, PreparedStatement, ResultSet, Timestamp}
import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField

trait DbProfile extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgRangeSupport
  with PgHStoreSupport
  with PgSearchSupport
  with PgNetSupport
  with PgLTreeSupport {

  override val api: ExtPostgresAPI = MyAPI

  object MyAPI extends ExtPostgresAPI with ArrayImplicits
    with Date2DateTimePlainImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants {

    // The timestampz can be received like this: '2021-11-03 23:17:16.440699+03' from postgresql
    // The default implementation doesn't support this
    override val date2TzDateTimeFormatter: DateTimeFormatter =
      new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND,0,6,true)
        .optionalEnd()
        .optionalStart()
        .appendOffset("+HH:mm","+00")
        .optionalEnd()
        .optionalStart()
        .appendOffset("+HH","+00")
        .optionalEnd()
        .toFormatter()

    override def offsetDateTimeColumnType: columnTypes.OffsetDateTimeJdbcType = {

      new columnTypes.OffsetDateTimeJdbcType {

        override val sqlType: Int = JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber

        override def setValue(v: OffsetDateTime, p: PreparedStatement, idx: Int): Unit = {
          p.setTimestamp(idx, Timestamp.from(v.toInstant))
        }

        override def getValue(r: ResultSet, idx: Int): OffsetDateTime = {
          r.getString(idx) match {
            case null => null
            case iso8601String : String => OffsetDateTime.parse(iso8601String, date2TzDateTimeFormatter)
          }
        }

        override def updateValue(v: OffsetDateTime, r: ResultSet, idx: Int): Unit = {
          r.updateTimestamp(idx, Timestamp.from(v.toInstant))
        }

        override def valueToSQLLiteral(value: OffsetDateTime): String = s"'${value.toString}'"

      }

    }

  }
}

object DbProfile extends DbProfile