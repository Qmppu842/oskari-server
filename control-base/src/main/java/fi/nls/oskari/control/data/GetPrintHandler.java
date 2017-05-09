package fi.nls.oskari.control.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.oskari.print.PrintFormat;
import org.oskari.print.PrintLayer;
import org.oskari.print.PrintRequest;
import org.oskari.print.Tile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.nls.oskari.annotation.OskariActionRoute;
import fi.nls.oskari.control.ActionException;
import fi.nls.oskari.control.ActionHandler;
import fi.nls.oskari.control.ActionParameters;
import fi.nls.oskari.control.ActionParamsException;
import fi.nls.oskari.control.layer.PermissionHelper;
import fi.nls.oskari.domain.User;
import fi.nls.oskari.domain.map.OskariLayer;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.ProxyService;
import fi.nls.oskari.util.ConversionHelper;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.ServiceFactory;
import fi.nls.oskari.wmts.domain.TileMatrix;

@OskariActionRoute("GetPrint")
public class GetPrintHandler extends ActionHandler {

    private static final Logger LOG = LogFactory.getLogger(GetPrintHandler.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PARM_COORD = "coord";
    private static final String PARM_RESOLUTION = "resolution";
    private static final String PARM_ZOOMLEVEL = "zoomLevel";
    private static final String PARM_PAGE_SIZE = "pageSize";
    private static final String PARM_MAPLAYERS = "mapLayers";
    private static final String PARM_FORMAT = "format";
    private static final String PARM_SRSNAME = "srs";

    private static final int DPI = 72;
    private static final double MM_PER_INCH = 25.4;

    private static final int A4W = 210;
    private static final int A4H = 297;
    private static final int A3W = 297;
    private static final int A3H = 420;

    // 10mm left + 10mm right
    private static final int MARGIN_WIDTH = 10 * 2;
    private static final int MARGIN_HEIGHT = 15 * 2;


    private static final String PRINT_URL = "http://localhost:8080/print/out/";
    private PermissionHelper permissionHelper;


    public void init() {
        permissionHelper = new PermissionHelper(
                ServiceFactory.getMapLayerService(), 
                ServiceFactory.getPermissionsService());
        ProxyService.init();
        HttpURLConnection.setFollowRedirects(false);
    }


    public void handleAction(ActionParameters params) throws ActionException {
        try {
            PrintRequest request = createPrintRequest(params);
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(request);

            HttpURLConnection con = IOHelper.getConnection(PRINT_URL);
            copyHeaders(con, params.getRequest());
            postRequest(con, json);
            passResponseThrough(con, params.getResponse());
        } catch (Exception e) {
            throw new ActionException("Couldn't handle print request", e);
        }
    }


    public static int mmToPx(int mm) {
        return (int) Math.round((double) (DPI * mm) / MM_PER_INCH);
    }


    private PrintRequest createPrintRequest(ActionParameters params)
            throws ActionException {
        PrintRequest request = new PrintRequest();

        request.setSrsName(params.getRequiredParam(PARM_SRSNAME));
        request.setZoomLevel(params.getRequiredParamInt(PARM_ZOOMLEVEL));
        request.setResolution(params.getRequiredParamDouble(PARM_RESOLUTION));

        setPagesize(params, request);
        setCoordinates(params.getRequiredParam(PARM_COORD), request);
        setFormat(params.getRequiredParam(PARM_FORMAT), request);

        List<PrintLayer> layers = getLayers(params.getRequiredParam(PARM_MAPLAYERS), 
                params.getUser(), request);
        setTiles(params, layers);

        return request;
    }


    private PrintLayer find(final List<PrintLayer> layers, final String layerId) {
        for (PrintLayer layer : layers) {
            if (layerId.equals(layer.getId())) {
                return layer;
            }
        }
        return null;
    }


    private void setPagesize(ActionParameters params, PrintRequest request) 
            throws ActionParamsException {
        String pageSizeStr = params.getRequiredParam(PARM_PAGE_SIZE);

        int width;
        int height;

        switch (pageSizeStr) {
        case "A4":
            width = A4W;
            height = A4H;
            break;
        case "A4_Landscape":
            width = A4H;
            height = A4W;
            break;
        case "A3":
            width = A3W;
            height = A3H;
            break;
        case "A3_Landscape":
            width = A3H;
            height = A3W;
            break;
        default:
            throw new ActionParamsException("Unknown pageSize");
        }

        width -= MARGIN_WIDTH;
        height -= MARGIN_HEIGHT;

        width = mmToPx(width);
        height = mmToPx(height);

        request.setWidth(width);
        request.setHeight(height);
    }


    private void setCoordinates(String coord, PrintRequest req) 
            throws ActionParamsException {
        int i = coord.indexOf('_');
        if (i < 0) {
            i = coord.indexOf(' ');
            if (i < 0) {
                throw new ActionParamsException("Could not parse coordinates from " + coord);
            }
        }

        double e = ConversionHelper.getDouble(coord.substring(0, i), Double.NaN);
        double n = ConversionHelper.getDouble(coord.substring(i + 1), Double.NaN);
        if (e == Double.NaN || n == Double.NaN) {
            throw new ActionParamsException("Could not parse coordinates from " + coord);
        }
        req.setEast(e);
        req.setNorth(n);
    }


    private void setFormat(String formatStr, PrintRequest req) 
            throws ActionParamsException {
        PrintFormat format = PrintFormat.getByContentType(formatStr);
        if (format == null) {
            throw new ActionParamsException("Invalid value for key '" + PARM_FORMAT + "'");
        }
        req.setFormat(format.contentType);
    }


    private List<PrintLayer> getLayers(String mapLayers, User user, PrintRequest request) 
            throws ActionException {
        LayerProperties[] requestedLayers = LayerProperties.parse(mapLayers);

        List<PrintLayer> printLayers = new ArrayList<>(requestedLayers.length);
        for (LayerProperties requestedLayer : requestedLayers) {
            OskariLayer oskariLayer = permissionHelper.getLayer(requestedLayer.getId(), user);
            PrintLayer printLayer = createPrintLayer(oskariLayer, requestedLayer);
            printLayers.add(printLayer);
        }

        return printLayers;
    }


    private PrintLayer createPrintLayer(OskariLayer oskariLayer, LayerProperties requestedLayer) {
        PrintLayer layer = new PrintLayer();
        layer.setId(oskariLayer.getExternalId());
        layer.setType(oskariLayer.getType());
        layer.setUrl(oskariLayer.getUrl());
        layer.setVersion(oskariLayer.getVersion());
        layer.setName(oskariLayer.getName());
        layer.setUsername(oskariLayer.getUsername());
        layer.setPassword(oskariLayer.getPassword());
        layer.setOpacity(getOpacity(requestedLayer.getOpacity(), oskariLayer.getOpacity()));
        return layer;
    }
    
    
    private int getOpacity(int requestedOpacity, Integer layersDefaultOpacity) {
        int opacity;
        if (requestedOpacity != LayerProperties.NULL) {
            // If the opacity is set in the request use that
            opacity = requestedOpacity;
        } else if (layersDefaultOpacity != null) {
            // Otherwise use layers default opacity
            opacity = layersDefaultOpacity;
        } else {
            // If that's missing or negative use 100 (full opacity)
            opacity = 100;
        }

        // Scale opacity to [0, 100]
        if (opacity < 0) {
            opacity = 0;
        } else if (opacity > 100) {
            opacity = 100;
        }

        return opacity;
    }


    // TODO: Replace parse logic with a simple POJO implementation
    private void setTiles(ActionParameters params, List<PrintLayer> layers)
            throws ActionException {
        try {
            String tilesJson = params.getHttpParam("PARM_TILES");
            
            JsonNode root = OBJECT_MAPPER.readTree(tilesJson);
            if (!root.isArray()) {
                throw new ActionParamsException("Tiles not array!");
            }
            
            Iterator<String> it = root.fieldNames();
            while (it.hasNext()) {
                String layerId = it.next();
                PrintLayer printLayer = find(layers, layerId);
                if (printLayer == null) {
                    continue;
                }
                
                JsonNode layerNode = root.get(layerId);
                int n = layerNode.size();
                if (n != 1) {
                    throw new ActionParamsException("Bad tiles! Size not 1");
                }
                
                JsonNode tilesNode = layerNode.get(0);
                if (!tilesNode.isArray()) {
                    throw new ActionParamsException("Bad tiles! Not array");
                }
                
                int m = tilesNode.size();
                Tile[] tiles = new Tile[m];
                
                for (int j = 0; j < m; j++) {
                    JsonNode tileNode = tilesNode.get(j);
                    
                    JsonNode bboxNode = tileNode.get("bbox");
                    if (bboxNode == null 
                            || !bboxNode.isArray() 
                            || bboxNode.size() != 4) {
                        throw new ActionParamsException("Bad tiles! "
                                + "Missing 'bbox' or not array or not size 4");
                    }
                    
                    JsonNode urlNode = tileNode.get("url");
                    if (urlNode == null || !urlNode.isTextual()) {
                        throw new ActionParamsException("Bad tiles! "
                                + "Missing 'url' or not text");
                    }
                    
                    double[] bbox = new double[4]; 
                    for (int k = 0; k < 4; k++) {
                        bbox[k] = bboxNode.get(k).asDouble();
                    }
                    String url = urlNode.asText();
                    
                    tiles[j] = new Tile(bbox, url);
                }
                
                printLayer.setTiles(tiles);
            }
        } catch (IOException e) {
            LOG.warn(e);
            throw new ActionException("Failed to parse tiles");
        }
    }


    private void copyHeaders(HttpURLConnection con, HttpServletRequest request) {
        for (Enumeration<String> e = request.getHeaderNames(); 
                e.hasMoreElements();) {
            final String key = e.nextElement();
            final String value = request.getHeader(key);
            if ("Content-Type".equals(key) || "Content-Length".equals(key)) {
                continue;
            }
            con.setRequestProperty(key, value);
        }
    }

    private void postRequest(HttpURLConnection con, byte[] body) throws IOException {
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setUseCaches(false);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Content-Length", Integer.toString(body.length));
        con.connect();

        if (LOG.isDebugEnabled()) {
            LOG.debug(new String(body, StandardCharsets.UTF_8));
        }

        try (OutputStream out = con.getOutputStream()) {
            out.write(body);
        }
    }


    private void passResponseThrough(HttpURLConnection con, HttpServletResponse response) 
            throws IOException {
        final int sc = con.getResponseCode();
        final int len = con.getContentLength();
        final String type = con.getContentType();

        response.setStatus(sc);
        response.setContentLength(len);
        response.setContentType(type);

        try (InputStream in = con.getInputStream();
                OutputStream out = response.getOutputStream()) {
            IOHelper.copy(in, out);
        }
    }

}
