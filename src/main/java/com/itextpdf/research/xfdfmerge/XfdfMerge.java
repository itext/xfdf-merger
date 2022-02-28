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
package com.itextpdf.research.xfdfmerge;

import com.itextpdf.commons.utils.MessageFormatUtil;
import com.itextpdf.forms.xfdf.AnnotObject;
import com.itextpdf.forms.xfdf.AnnotsObject;
import com.itextpdf.forms.xfdf.XfdfAnnotFactory;
import com.itextpdf.forms.xfdf.XfdfConstants;
import com.itextpdf.forms.xfdf.XfdfObject;
import com.itextpdf.forms.xfdf.XfdfObjectReadingUtils;
import com.itextpdf.io.logs.IoLogMessageConstant;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.AffineTransform;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfCaretAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfFreeTextAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfMarkupAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfPopupAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfStampAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfTextMarkupAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XfdfMerge {

    private static final Logger LOGGER = LoggerFactory.getLogger(XfdfMerge.class);
    private final Color DEFAULT_HIGHLIGHT_COLOR = new DeviceRgb(1f, 0.81f, 0f);
    private final PdfDocument pdfDocument;
    private final Map<String, PdfAnnotation> annotMap = new HashMap<>();
    private final Map<String, List<PdfMarkupAnnotation>> replyMap = new HashMap<>();
    private PdfFormXObject caretXObj = null;
    private PdfFormXObject commentXObj = null;
    private final AffineTransform transform;
    private final int pageShift;

    public XfdfMerge(PdfDocument pdfDocument, AffineTransform transform, int pageShift) {
        this.pdfDocument = pdfDocument;
        this.transform = transform;
        this.pageShift = pageShift;
    }

    void mergeXfdfIntoPdf(XfdfObject xfdfObject) {
        mergeAnnotations(xfdfObject.getAnnots());
    }

    /**
     * Merges existing XfdfObject into pdf document associated with it.
     *
     * @param annotsObject    The AnnotsObject with children AnnotObject entities to be mapped into PdfAnnotations.
     */
    private void mergeAnnotations(AnnotsObject annotsObject) {
        List<AnnotObject> annotList = null;
        if (annotsObject != null) {
            annotList = annotsObject.getAnnotsList();
        }

        if (annotList != null && !annotList.isEmpty()) {
            for (AnnotObject annot : annotList) {
                addAnnotationToPdf(annot);
            }
        }
    }

    private Color getAnnotColor(AnnotObject annotObject, Color defaultColor) {
        String colorString = annotObject.getAttributeValue(XfdfConstants.COLOR);
        if(colorString != null) {
            int[] rgbValues = XfdfObjectReadingUtils.convertColorFloatsFromString(colorString);
            return new DeviceRgb(rgbValues[0], rgbValues[1], rgbValues[2]);
        } else {
            return defaultColor;
        }
    }

    private void addCommonAnnotationAttributes(PdfAnnotation annotation, AnnotObject annotObject, Color color) {
        annotation.setFlags(XfdfObjectReadingUtils.convertFlagsFromString(annotObject.getAttributeValue(XfdfConstants.FLAGS)));
        annotation.setColor(color);
        String dateString = annotObject.getAttributeValue(XfdfConstants.DATE);
        if(dateString != null) {
            annotation.setDate(new PdfString(dateString));
        }
        String name = annotObject.getAttributeValue(XfdfConstants.NAME);
        if(name != null) {
            annotation.setName(new PdfString(name));
            annotMap.put(name, annotation);
            // add pending replies
            for(PdfMarkupAnnotation reply : replyMap.getOrDefault(name, Collections.emptyList())) {
                reply.setInReplyTo(annotation);
            }
            replyMap.remove(name);
        }
        String titleString = annotObject.getAttributeValue(XfdfConstants.TITLE);
        if(titleString != null) {
            annotation.setTitle(new PdfString(titleString));
        }
    }

    private void addPopupAnnotation(int page, PdfMarkupAnnotation parent, AnnotObject popup) {
        if(popup != null) {
            PdfPopupAnnotation pdfPopupAnnot = new PdfPopupAnnotation(readAnnotRect(popup));
            // TODO set Open based on value in XFDF
            pdfPopupAnnot.setOpen(false)
                    .setFlags(XfdfObjectReadingUtils.convertFlagsFromString(popup.getAttributeValue(XfdfConstants.FLAGS)));
            parent.setPopup(pdfPopupAnnot);
            pdfDocument.getPage(page).addAnnotation(pdfPopupAnnot);
        }
    }

    private void addMarkupAnnotationAttributes(PdfMarkupAnnotation annotation, AnnotObject annotObject) {
        String creationDateString = annotObject.getAttributeValue(XfdfConstants.CREATION_DATE);
        if(creationDateString != null) {
            annotation.setCreationDate(new PdfString(creationDateString));
        }
        String subjectString = annotObject.getAttributeValue(XfdfConstants.SUBJECT);
        if(subjectString != null) {
            annotation.setSubject(new PdfString(subjectString));
        }
        String intent = annotObject.getAttributeValue("IT");
        if(intent != null && !intent.isBlank()) {
            annotation.setIntent(new PdfName(intent));
        }

        String irpt = annotObject.getAttributeValue(XfdfConstants.IN_REPLY_TO);
        if(irpt != null && !irpt.isBlank()) {
            if("group".equalsIgnoreCase(annotObject.getAttributeValue(XfdfConstants.REPLY_TYPE))) {
                annotation.setReplyType(PdfName.Group);
            }
            PdfAnnotation inReplyToAnnot = annotMap.get(irpt);
            if(inReplyToAnnot != null) {
                annotation.setInReplyTo(inReplyToAnnot);
            } else {
                // queue for later
                List<PdfMarkupAnnotation> queued = replyMap.get(irpt);
                if(queued == null) {
                    queued = new ArrayList<>();
                    queued.add(annotation);
                    replyMap.put(irpt, queued);
                } else {
                    queued.add(annotation);
                }
            }
        }

        PdfString rc = annotObject.getContentsRichText();
        if(rc != null && !rc.toString().isBlank()) {
            String rcString = rc.toString().trim();
            annotation.setRichText(new PdfString(rcString));
        }

        PdfString plainContents = annotObject.getContents();
        if(plainContents != null && !plainContents.toString().isBlank()) {
            String pcString = plainContents.toString().trim();
            annotation.setContents(new PdfString(pcString));
        }
    }

    private Rectangle readAnnotRect(AnnotObject annotObject) {
        String rect = annotObject.getAttributeValue(XfdfConstants.RECT);
        return XfdfObjectReadingUtils.convertRectFromString(rect, this.transform);
    }

    private float[] readAnnotQuadPoints(AnnotObject annotObject) {
        String coords = annotObject.getAttributeValue(XfdfConstants.COORDS);
        return XfdfObjectReadingUtils.convertQuadPointsFromCoordsString(coords, this.transform);
    }

    private int readAnnotPage(AnnotObject annotObject) {
        // iText pages are 1-indexed
        int page = 1 + Integer.parseInt(annotObject.getAttribute(XfdfConstants.PAGE).getValue());
        return this.pageShift + page;
    }

    private PdfFormXObject getCaretAppearance(Color color) {
        if(this.caretXObj != null) {
            return this.caretXObj;
        }
        // draw a caret on a 30x30 canvas
        this.caretXObj = new PdfFormXObject(new Rectangle(30, 30));
        PdfCanvas canvas = new PdfCanvas(this.caretXObj, this.pdfDocument);
        canvas.setFillColor(color)
                .moveTo(15, 30)
                .curveTo(15, 30, 15, 0, 0, 0)
                .lineTo(30, 0)
                .curveTo(15, 0, 15,30, 15, 30)
                .closePath()
                .fill();
        return this.caretXObj;
    }

    private PdfFormXObject getCommentAppearance(Color color) {
        if(this.commentXObj != null) {
            return this.commentXObj;
        }
        // draw a speech bubble on a 30x30 canvas
        this.commentXObj = new PdfFormXObject(new Rectangle(30, 30));
        PdfCanvas canvas = new PdfCanvas(this.commentXObj, this.pdfDocument);

        canvas.setFillColor(color).setLineWidth(0.85f)
                .moveTo(6, 27.5)
                .curveTo(4.3, 27.5, 3, 26.5, 3, 25)
                .lineTo(3, 12)
                .curveTo(3, 10.25, 4.3, 10.25, 6, 10.25)
                .lineTo(7.6, 10.25)
                .lineTo(11.25, 3)
                .lineTo(13, 10.25)
                .lineTo(25.5, 10.25)
                .curveTo(25.1, 10.25, 26.25, 10.25, 26.25, 12)
                .lineTo(26.25, 25)
                .curveTo(26.25, 26.5, 25, 27.5, 23.5, 27.5)
                .closePath().fill();
        return this.commentXObj;
    }

    private void addTextMarkupAnnotationToPdf(PdfName subtype, AnnotObject annotObject, Color color) {
        Rectangle rect = readAnnotRect(annotObject);
        float[] quads = readAnnotQuadPoints(annotObject);
        PdfTextMarkupAnnotation pdfAnnot = new PdfTextMarkupAnnotation(rect, subtype, quads);

        addCommonAnnotationAttributes(pdfAnnot, annotObject, color);
        addMarkupAnnotationAttributes(pdfAnnot, annotObject);
        int page = readAnnotPage(annotObject);
        pdfDocument.getPage(page).addAnnotation(pdfAnnot);
        addPopupAnnotation(page, pdfAnnot, annotObject.getPopup());
    }

    private Color getDefaultColor(String annotName) {
        switch (annotName) {
            case XfdfConstants.TEXT:
            case XfdfConstants.HIGHLIGHT:
                return DEFAULT_HIGHLIGHT_COLOR;
            case XfdfConstants.UNDERLINE:
            case XfdfConstants.STRIKEOUT:
            case XfdfConstants.SQUIGGLY:
                return DeviceRgb.RED;
            case XfdfConstants.CARET:
                return DeviceRgb.BLUE;
            default:
                return DeviceRgb.BLACK;
        }
    }

    private void addAnnotationToPdf(AnnotObject annotObject) {
        String annotName = annotObject.getName();
        int page;
        if (annotName != null) {
            Color color = getAnnotColor(annotObject, getDefaultColor(annotName));
            switch (annotName) {
                case XfdfConstants.TEXT:
                    PdfTextAnnotation pdfTextAnnotation = new PdfTextAnnotation(readAnnotRect(annotObject));
                    addCommonAnnotationAttributes(pdfTextAnnotation, annotObject, color);
                    addMarkupAnnotationAttributes(pdfTextAnnotation, annotObject);

                    String icon = annotObject.getAttributeValue(XfdfConstants.ICON);
                    if("Comment".equals(icon)) {
                        pdfTextAnnotation.setNormalAppearance(this.getCommentAppearance(color).getPdfObject());
                    }
                    pdfTextAnnotation.setIconName(new PdfName(icon));
                    String stateString = annotObject.getAttributeValue(XfdfConstants.STATE);
                    if(stateString != null) {
                        pdfTextAnnotation.setState(new PdfString(stateString));
                    }
                    String stateModelString = annotObject.getAttributeValue(XfdfConstants.STATE_MODEL);
                    if(stateModelString != null) {
                        pdfTextAnnotation.setStateModel(new PdfString(stateModelString));
                    }

                    page = readAnnotPage(annotObject);
                    pdfDocument.getPage(page).addAnnotation(pdfTextAnnotation);
                    addPopupAnnotation(page, pdfTextAnnotation, annotObject.getPopup());
                    break;
                case XfdfConstants.HIGHLIGHT:
                    addTextMarkupAnnotationToPdf(PdfName.Highlight, annotObject, color);
                    break;
                case XfdfConstants.UNDERLINE:
                    addTextMarkupAnnotationToPdf(PdfName.Underline, annotObject, color);
                    break;
                case XfdfConstants.STRIKEOUT:
                    addTextMarkupAnnotationToPdf(PdfName.StrikeOut, annotObject, color);
                    break;
                case XfdfConstants.SQUIGGLY:
                    addTextMarkupAnnotationToPdf(PdfName.Squiggly, annotObject, color);
                    break;
                case XfdfConstants.CARET:
                    PdfCaretAnnotation caretAnnotation = new PdfCaretAnnotation(readAnnotRect(annotObject));
                    caretAnnotation.setNormalAppearance(this.getCaretAppearance(color).getPdfObject());
                    addCommonAnnotationAttributes(caretAnnotation, annotObject, color);
                    addMarkupAnnotationAttributes(caretAnnotation, annotObject);
                    page = readAnnotPage(annotObject);
                    pdfDocument.getPage(page).addAnnotation(caretAnnotation);
                    addPopupAnnotation(page, caretAnnotation, annotObject.getPopup());
                    break;
                case XfdfConstants.STAMP:
                    pdfDocument.getPage(readAnnotPage(annotObject))
                            .addAnnotation(new PdfStampAnnotation(readAnnotRect(annotObject)));
                    break;
                case XfdfConstants.FREETEXT:
                    PdfFreeTextAnnotation freeText =
                            new PdfFreeTextAnnotation(readAnnotRect(annotObject), annotObject.getContents());
                    pdfDocument.getPage(readAnnotPage(annotObject)).addAnnotation(freeText);
                    break;
                default:
                    LOGGER.warn(MessageFormatUtil.format(IoLogMessageConstant.XFDF_ANNOTATION_IS_NOT_SUPPORTED, annotName));
                    break;
            }

        }
    }

    private static final String USAGE = "Usage: XfdfMerge input.pdf input.xfdf output.pdf [PGNUMSHIFT/XSHIFT/YSHIFT/SCALE]";
    public static void main(String[] args) throws Exception {
        if(args.length != 3 && args.length != 4) {
            System.err.println(USAGE);
            return;
        }
        String pdfIn = args[0];
        String xfdfIn = args[1];
        String pdfOut = args[2];

        AffineTransform transform;
        int pageShift;
        if(args.length == 4) {
            // process transformation argument
            String[] split = args[3].split("/");
            if(split.length != 4) {
                System.err.println(USAGE);
                return;
            }
            try {
                pageShift = Integer.parseInt(split[0]);
                double xShift = Double.parseDouble(split[1]);
                double yShift = Double.parseDouble(split[2]);
                double scale = Double.parseDouble(split[3]);
                double[] matrix = new double[] {scale, 0, 0, scale, xShift, yShift};
                transform = new AffineTransform(matrix);
                LOGGER.info(
                        "Applying transformation {}" + "; page number shift={}",
                        Arrays.toString(matrix),
                        pageShift);
            } catch(NumberFormatException nfe) {
                System.err.println(USAGE);
                return;
            }
        } else {
            transform = new AffineTransform();
            pageShift = 0;
        }

        XfdfObject xfdfRoot;
        try(InputStream is = new FileInputStream(xfdfIn)) {
            xfdfRoot = new XfdfAnnotFactory().createXfdfObject(is);
        }
        StampingProperties sp = new StampingProperties().useAppendMode();
        try(PdfReader r = new PdfReader(pdfIn);
            PdfWriter w = new PdfWriter(pdfOut);
            PdfDocument pdfDoc = new PdfDocument(r, w, sp)) {
            XfdfMerge mrg = new XfdfMerge(pdfDoc, transform, pageShift);
            mrg.mergeXfdfIntoPdf(xfdfRoot);
        }
    }
}
