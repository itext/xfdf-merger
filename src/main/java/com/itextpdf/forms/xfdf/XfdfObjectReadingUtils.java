/*

    Copyright (c) 2022 iText Group NV

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

 */
package com.itextpdf.forms.xfdf;

import com.itextpdf.kernel.geom.AffineTransform;
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
    public static Rectangle convertRectFromString(String rectString, AffineTransform transf) {
        String delims = ",";
        StringTokenizer st = new StringTokenizer(rectString, delims);
        List<String> coordsList = new ArrayList<>();

        while (st.hasMoreTokens()) {
            coordsList.add(st.nextToken());
        }

        if (coordsList.size() == 2) {
            return new Rectangle(Float.parseFloat(coordsList.get(0)), Float.parseFloat(coordsList.get(1)));
        } else if (coordsList.size() == 4) {
            float[] rawCoords = new float[] {
                    Float.parseFloat(coordsList.get(0)),
                    Float.parseFloat(coordsList.get(1)),
                    Float.parseFloat(coordsList.get(2)),
                    Float.parseFloat(coordsList.get(3))
            };
            float[] coords = new float[4];
            transf.transform(rawCoords, 0, coords, 0, 2);
            return new Rectangle(coords[0], coords[1], Math.abs(coords[0] - coords[2]), Math.abs(coords[1] - coords[3]));
        }

        throw new IllegalArgumentException();
    }

    /**
     * Converts a string containing 4 float values into a float array, representing quadPoints.
     * If the number of floats in the string is not equal to 8, returns an empty float array.
     */
    public static float [] convertQuadPointsFromCoordsString(String coordsString, AffineTransform transf) {
        String delims = ",";
        StringTokenizer st = new StringTokenizer(coordsString, delims);
        List<String> quadPointsList = new ArrayList<>();

        while (st.hasMoreTokens()) {
            quadPointsList.add(st.nextToken());
        }

        int sz = quadPointsList.size();
        if (sz % 8 != 0) {
            return new float[0];
        }
        float [] quadPoints = new float [sz];
        for (int i = 0; i < sz; i++) {
            quadPoints[i] = Float.parseFloat(quadPointsList.get(i));
        }
        float[] transfQuadPoints = new float[sz];
        transf.transform(quadPoints, 0, transfQuadPoints, 0, sz / 2);
        return quadPoints;
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
    public static int[] convertColorFloatsFromString(String colorHexString) {
        int[] result = new int[3];
        String colorString = colorHexString.substring(colorHexString.indexOf('#') + 1);
        if (colorString.length() == 6) {
            for (int i = 0; i < 3; i++) {
                result[i] = Integer.parseInt(colorString.substring(i * 2, 2 + i * 2), 16);
            }
        }
        return result;
    }

}
