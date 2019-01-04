package me.osm.gazetteer.striper.builders

import org.slf4j.LoggerFactory
import org.scalatest._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.language.implicitConversions

import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.io.{WKTReader, WKTWriter}

import me.osm.gazetteer.striper.readers.RelationsReader


class BuildUtilsTest extends FlatSpec with Matchers {
    private val logger = LoggerFactory.getLogger(this.getClass)

    // spatial reference
    private val WGS84 = 4326

    // fixed precision model
    private implicit val gf: GeometryFactory = new GeometryFactory(new PrecisionModel(10000), WGS84)

    // todo: geojson?
    private val wktwriter = new WKTWriter()
    private val wktreader = new WKTReader(gf)

    // default OSM relation
    private val relstub = new RelationsReader.Relation { id = 42 }

    // default compare tolerance
    private implicit val tolerance: Double = 0.0001

    private def geometries(geomColl: GeometryCollection): Iterable[Geometry] = {
        val res = for {
            n <- 0 until geomColl.getNumGeometries
        } yield geomColl.getGeometryN(n)

        res
    }

    private def polygonOuter(g: Geometry): LineString = {
        g.asInstanceOf[Polygon].getExteriorRing
    }

    private def polygonInners(g: Geometry): Iterable[LineString] = {
        val p = g.asInstanceOf[Polygon]
        val res = for {
            n <- 0 until p.getNumInteriorRing
        } yield p.getInteriorRingN(n)

        res
    }

    import Toolbox.StringOps

    private def buildCoordinate(xy: Seq[String]): Coordinate = {
        new Coordinate(xy(0).toDouble, xy(1).toDouble)
    }

    private def loadLineString(str: String)(implicit gf: GeometryFactory): LineString = {
        val coords = str.toArray(",").map(xy => {
            buildCoordinate(xy.toList(" "))
        })

        gf.createLineString(coords)
    }

    private def loadWaysFromText(txt: String): Iterable[LineString] = {
        txt.toList("\n").map(s => loadLineString(s))
    }

    private def ringEqual(left: LineString, right: String)(implicit tolerance: Double): Boolean = {
        val leftPoints = left.getCoordinates.dropRight(1)
        val rightPoints = loadLineString(right).getCoordinates

        def pointsEqual(p1: Coordinate, p2: Coordinate): Boolean = {
            p1.x === (p2.x +- tolerance) &&
                p1.y === (p2.y +- tolerance)
        }

        @tailrec
        def checkPairs(left: Array[Coordinate], right: Array[Coordinate], cnt: Int): Boolean = {
            if (cnt <= 0) false
            else {
                val pairs = left.zip(right)
                if (pairs.forall(p => pointsEqual(p._1, p._2))) true
                else checkPairs(left.tail :+ left.head, right, cnt - 1)
            }
        }

        if (leftPoints.length == rightPoints.length) {
            require(leftPoints.length > 2, "ring must have 3 points at least")
            checkPairs(leftPoints, rightPoints, leftPoints.length)
        }
        else false
    }

    implicit def iterable2jul[T](it: Iterable[T]): java.util.List[T] = it.toList.asJava

    // gazetteer/testOnly me.osm.gazetteer.striper.builders.BuildUtilsTest -- -z "multipolygon from WKT"
    it should "build multipolygon from WKT" in {
        // define input
        val mpolywkt =
            """
              |MULTIPOLYGON ((
              |(20 35, 10 10, 45 20, 20 35),
              | (30 20, 20 15, 20 25, 30 20)
              |))
            """.stripMargin.trim.replaceAll("\n", "")

        // load input
        val mpoly = wktreader.read(mpolywkt).asInstanceOf[MultiPolygon]
        assert(wktwriter.write(mpoly) === mpolywkt)
        val polygons = geometries(mpoly)
        val outers = polygons.map(p => polygonOuter(p))
        val inners = polygons.flatMap(p => polygonInners(p))

        // generate output
        val g = BuildUtils.buildMultyPolygon(logger, relstub, outers, inners)
            .reverse()
        val wkt = wktwriter.write(g)

        // check output
        println("SRC: " + mpolywkt); println("RES: " + wkt)
        assert(wkt === mpolywkt)
        assert(mpoly.getArea === g.getArea)
        assert(mpoly.getCentroid.equalsExact(g.getCentroid, tolerance))
    }

    // gazetteer/testOnly me.osm.gazetteer.striper.builders.BuildUtilsTest -- -z "multipolygon from text"
    it should "build multipolygon from text" in {
        // define input
        val outerWays =
            """
              |20 35, 10 10, 45 20, 20 35
            """.stripMargin.trim
        val innerWays =
            """
              |30 20, 20 15, 20 25, 30 20
            """.stripMargin.trim

        // load input
        val outers = loadWaysFromText(outerWays)
        val inners = loadWaysFromText(innerWays)

        // generate output
        val g = BuildUtils.buildMultyPolygon(logger, relstub, outers, inners)
            .reverse()
        val wkt = wktwriter.write(g)

        // check output
        assert(wkt === "MULTIPOLYGON (((20 35, 10 10, 45 20, 20 35), (30 20, 20 15, 20 25, 30 20)))")
        assert(g.getArea === (337.5 +- tolerance))
        assert(g.getCentroid.getX === (25.2469 +- tolerance))
        assert(g.getCentroid.getY === (21.9135 +- tolerance))
    }

    // gazetteer/testOnly me.osm.gazetteer.striper.builders.BuildUtilsTest -- -z "multipolygon from multi text"
    it should "build multipolygon from multi text" in {
        // define input
        val outerWays =
            """
              |20 35, 10 10
              |10 10, 45 20
              |45 20, 20 35
            """.stripMargin.trim
        val innerWays =
            """
              |30 20, 20 15
              |20 15, 20 25
              |20 25, 30 20
            """.stripMargin.trim

        // load input
        val outers = loadWaysFromText(outerWays)
        val inners = loadWaysFromText(innerWays)

        // generate output
        val g = BuildUtils.buildMultyPolygon(logger, relstub, outers, inners)
            .reverse()
        val wkt = wktwriter.write(g)
        println("RES: " + wkt)

        // check output
        assert(ringEqual(polygonOuter(g.getGeometryN(0)), "20 35, 10 10, 45 20"))
        assert(ringEqual(polygonInners(g.getGeometryN(0)).head, "30 20, 20 15, 20 25"))
        assert(g.getArea === (337.5 +- tolerance))
        assert(g.getCentroid.getX === (25.2469 +- tolerance))
        assert(g.getCentroid.getY === (21.9135 +- tolerance))
    }
}

object Toolbox {

    implicit class StringOps(val src: String) extends AnyVal {

        /**
          * trim src, split src by regexp(sep surrounded by blanks), drop empty items
          */
        def toArray(implicit sep: String = ","): Array[String] =
            src.trim.split("""\s*""" + sep + """\s*""").filterNot(_.isEmpty)

        /**
          * split string by sep, trim every item, convert array to list, drop empty items
          */
        def toList(sep: String): Seq[String] = src.split(sep).map(_.trim).toSeq.filter(_.nonEmpty)

        /**
          * Create Map from string, e.g. "foo: bar; poo: bazz"
          */
        def loadMap(implicit pairsSep: String = ";", kvSep: String = ":"): Map[String, String] = {
            src.toArray(pairsSep)
                .flatMap(s => s.toArray(kvSep) match {
                    case Array(k, v) => Some(k -> v)
                    case _ => None
                } ).toMap
        }
    }
}
