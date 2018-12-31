package me.osm.gazetteer.striper.builders

import org.slf4j.LoggerFactory
import org.scalatest._

import com.vividsolutions.jts.geom._
import com.vividsolutions.jts.io.{WKTReader, WKTWriter}

import me.osm.gazetteer.striper.readers.RelationsReader

import collection.JavaConverters._


class BuildUtilsTest extends FlatSpec with Matchers {
    private val logger = LoggerFactory.getLogger(this.getClass)

    // spatial reference
    private val WGS84 = 4326

    // fixed precision model
    private val gf = new GeometryFactory(new PrecisionModel(10000), WGS84)

    // todo: geojson?
    private val wktwriter = new WKTWriter()
    private val wktreader = new WKTReader(gf)

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

    implicit def iterable2jul[T](it: Iterable[T]): java.util.List[T] = it.toList.asJava

    // gazetteer/testOnly me.osm.gazetteer.striper.builders.BuildUtilsTest -- -z "multipolygon"
    it should "build multipolygon" in {
        val relstub = new RelationsReader.Relation { id = 42 }
        val mpolywkt =
            """
              |MULTIPOLYGON ((
              |(20 35, 10 10, 45 20, 20 35),
              | (30 20, 20 15, 20 25, 30 20)
              |))
            """.stripMargin.trim.replaceAll("\n", "")
        val mpoly = wktreader.read(mpolywkt).asInstanceOf[MultiPolygon]
        assert(wktwriter.write(mpoly) === mpolywkt)

        val polygons = geometries(mpoly)
        val outers = polygons.map(p => polygonOuter(p))
        val inners = polygons.flatMap(p => polygonInners(p))

        val g = BuildUtils.buildMultyPolygon(logger, relstub, outers, inners)
            .reverse()
        val wkt = wktwriter.write(g)

        println("RES: " + wkt); println("SRC: " + mpolywkt)
        println(s"area: ${g.getArea}")

        assert(wkt === mpolywkt)
        assert(mpoly.getArea === g.getArea)
        assert(mpoly.getCentroid.equalsExact(g.getCentroid, 0.0001))
    }
}
