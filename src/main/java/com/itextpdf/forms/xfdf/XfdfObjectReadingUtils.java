package com.itextpdf.forms.xfdf;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Ripped out of XfdfObjectUtils.
 */
public final class XfdfObjectReadingUtils {

    private XfdfObjectReadingUtils() {
    }

    /**
     * Converts a string containing 2 or 4 float values into a {@link Rectangle}.
     * If only two coordinates are present, they should represent {@link Rectangle} width and height.
     */
    public static Rectangle convertRectFromString(String rectString) {
        String delims = ",";
        StringTokenizer st = new StringTokenizer(rectString, delims);
        List<String> coordsList = new ArrayList<>();

        while (st.hasMoreTokens()) {
            coordsList.add(st.nextToken());
        }

        if (coordsList.size() == 2) {
            return new Rectangle(Float.parseFloat(coordsList.get(0)), Float.parseFloat(coordsList.get(1)));
        } else if (coordsList.size() == 4) {
            float x1 = Float.parseFloat(coordsList.get(0));
            float y1 = Float.parseFloat(coordsList.get(1));
            float x2 = Float.parseFloat(coordsList.get(2));
            float y2 = Float.parseFloat(coordsList.get(3));
            return new Rectangle(x1, y1, Math.abs(x1 - x2), Math.abs(y1 - y2));
        }

        throw new IllegalArgumentException();
    }

    /**
     * Converts a string containing 4 float values into a float array, representing quadPoints.
     * If the number of floats in the string is not equal to 8, returns an empty float array.
     */
    public static float [] convertQuadPointsFromCoordsString(String coordsString) {
        String delims = ",";
        StringTokenizer st = new StringTokenizer(coordsString, delims);
        List<String> quadPointsList = new ArrayList<>();

        while (st.hasMoreTokens()) {
            quadPointsList.add(st.nextToken());
        }

        if (quadPointsList.size() == 8) {
            float [] quadPoints = new float [8];
            for (int i = 0; i < 8; i++) {
                quadPoints[i] = Float.parseFloat(quadPointsList.get(i));
            }
            return quadPoints;
        }
        return new float[0];
    }

    /**
     * Converts a string containing a comma separated list of names of the flags into an integer representation
     * of the flags.
     */
    public static int convertFlagsFromString(String flagsString) {
        int result = 0;

        String delims = ",";
        StringTokenizer st = new StringTokenizer(flagsString, delims);
        List<String> flagsList = new ArrayList<>();
        while (st.hasMoreTokens()) {
            flagsList.add(st.nextToken().toLowerCase());
        }

        Map<String, Integer> flagMap = new HashMap<>();
        flagMap.put(XfdfConstants.INVISIBLE, PdfAnnotation.INVISIBLE);
        flagMap.put(XfdfConstants.HIDDEN, PdfAnnotation.HIDDEN);
        flagMap.put(XfdfConstants.PRINT, PdfAnnotation.PRINT);
        flagMap.put(XfdfConstants.NO_ZOOM, PdfAnnotation.NO_ZOOM);
        flagMap.put(XfdfConstants.NO_ROTATE, PdfAnnotation.NO_ROTATE);
        flagMap.put(XfdfConstants.NO_VIEW, PdfAnnotation.NO_VIEW);
        flagMap.put(XfdfConstants.READ_ONLY, PdfAnnotation.READ_ONLY);
        flagMap.put(XfdfConstants.LOCKED, PdfAnnotation.LOCKED);
        flagMap.put(XfdfConstants.TOGGLE_NO_VIEW, PdfAnnotation.TOGGLE_NO_VIEW);

        for(String flag : flagsList) {
            if (flagMap.containsKey(flag)) {
                result += flagMap.get(flag);
            }
        }
        return result;
    }

    /**
     * Converts string containing hex color code into an array of 3 integer values representing rgb color.
     */
    public static float[] convertColorFloatsFromString(String colorHexString){
        float[] result = new float[3];
        String colorString = colorHexString.substring(colorHexString.indexOf('#') + 1);
        if (colorString.length() == 6) {
            for (int i = 0; i < 3; i++) {
                result[i] = Integer.parseInt(colorString.substring(i * 2, 2 + i * 2), 16);
            }
        }
        return result;
    }

}
