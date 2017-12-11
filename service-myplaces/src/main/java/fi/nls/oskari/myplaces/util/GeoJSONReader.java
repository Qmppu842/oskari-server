package fi.nls.oskari.myplaces.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class GeoJSONReader {

    private static final GeometryFactory GF = new GeometryFactory();

    private static final String GEOMETRY = "geometry";
    private static final String TYPE = "type";
    private static final String COORDINATES = "coordinates";

    public static Geometry getGeometry(JSONObject feature) throws JSONException {
        JSONObject geometry = feature.getJSONObject(GEOMETRY);
        String geomType = geometry.getString(TYPE);
        switch (geomType) {
        case "Point":
            return getPoint(geometry);
        case "LineString":
            return getLineString(geometry);
        case "Polygon":
            return getPolygon(geometry);
        case "MultiPoint":
            return getMultiPoint(geometry);
        case "MultiLineString":
            return getMultiLineString(geometry);
        case "MultiPolygon":
            return getMultiPolygon(geometry);
        case "GeometryCollection":
            return getGeometryCollection(geometry);
        default:
            throw new IllegalArgumentException("Invalid geometry type");
        }
    }

    public static Point getPoint(JSONObject geometry)
            throws JSONException {
        return GF.createPoint(toCoordinate(geometry.getJSONArray(COORDINATES)));
    }

    public static LineString getLineString(JSONObject geometry)
            throws JSONException {
        Coordinate[] coordinates = toCoordinates(geometry.getJSONArray(COORDINATES));
        return GF.createLineString(coordinates);
    }

    public static Polygon getPolygon(JSONObject geometry)
            throws JSONException {
        return getPolygon(geometry.getJSONArray(COORDINATES));
    }

    public static MultiPoint getMultiPoint(JSONObject geometry)
            throws JSONException {
        Coordinate[] coordinates = toCoordinates(geometry.getJSONArray(COORDINATES));
        return GF.createMultiPoint(coordinates);
    }

    public static MultiLineString getMultiLineString(JSONObject geometry)
            throws JSONException {
        Coordinate[][] coordinates = toCoordinatesArray(geometry.getJSONArray(COORDINATES));
        int n = coordinates.length;
        LineString[] lineStrings = new LineString[n];
        for (int i = 0; i < n; i++) {
            lineStrings[i] = GF.createLineString(coordinates[i]);
        }
        return GF.createMultiLineString(lineStrings);
    }

    public static MultiPolygon getMultiPolygon(JSONObject geometry)
            throws JSONException {
        JSONArray arrayOfPolygons = geometry.getJSONArray(COORDINATES);
        int n = arrayOfPolygons.length();
        Polygon[] polygons = new Polygon[n];
        for (int i = 0; i < n; i++) {
            polygons[i] = getPolygon(arrayOfPolygons.getJSONArray(i));
        }
        return GF.createMultiPolygon(polygons);
    }

    public static GeometryCollection getGeometryCollection(JSONObject geometry)
            throws JSONException {
        JSONArray geometryArray = geometry.getJSONArray("geometry");
        int n = geometryArray.length();
        Geometry[] geometries = new Geometry[n];
        for (int i = 0; i < n; i++) {
            geometries[i] = getGeometry(geometryArray.getJSONObject(i));
        }
        return GF.createGeometryCollection(geometries);
    }

    private static Coordinate toCoordinate(JSONArray coordinate)
            throws JSONException {
        return new Coordinate(coordinate.getDouble(0), coordinate.getDouble(1));
    }

    private static Coordinate[] toCoordinates(JSONArray arrayOfCoordinates)
            throws JSONException {
        int n = arrayOfCoordinates.length();
        Coordinate[] coordinates = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            coordinates[i] = toCoordinate(arrayOfCoordinates.getJSONArray(i));
        }
        return coordinates;
    }

    private static Coordinate[][] toCoordinatesArray(JSONArray arrayOfArrayOfCoordinates)
            throws JSONException {
        int n = arrayOfArrayOfCoordinates.length();
        Coordinate[][] coordinates = new Coordinate[n][];
        for (int i = 0; i < n; i++) {
            coordinates[i] = toCoordinates(arrayOfArrayOfCoordinates.getJSONArray(i));
        }
        return coordinates;
    }

    private static Polygon getPolygon(JSONArray coordinatesArray)
            throws JSONException {
        Coordinate[][] coordinates = toCoordinatesArray(coordinatesArray);
        LinearRing exterior = GF.createLinearRing(coordinates[0]);
        LinearRing[] interiors = new LinearRing[coordinates.length - 1];
        for (int i = 1; i < coordinates.length; i++) {
            interiors[i - 1] = GF.createLinearRing(coordinates[i]);
        }
        return GF.createPolygon(exterior, interiors);
    }
}
