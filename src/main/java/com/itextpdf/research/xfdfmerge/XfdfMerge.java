package com.itextpdf.research.xfdfmerge;

import com.itextpdf.commons.utils.MessageFormatUtil;
import com.itextpdf.forms.xfdf.AnnotObject;
import com.itextpdf.forms.xfdf.AnnotsObject;
import com.itextpdf.forms.xfdf.XfdfAnnotFactory;
import com.itextpdf.forms.xfdf.XfdfConstants;
import com.itextpdf.forms.xfdf.XfdfObject;
import com.itextpdf.forms.xfdf.XfdfObjectReadingUtils;
import com.itextpdf.io.logs.IoLogMessageConstant;
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

    private void addCommonAnnotationAttributes(PdfAnnotation annotation, AnnotObject annotObject) {
        annotation.setFlags(XfdfObjectReadingUtils.convertFlagsFromString(annotObject.getAttributeValue(XfdfConstants.FLAGS)));
        annotation.setColor(XfdfObjectReadingUtils.convertColorFloatsFromString(annotObject.getAttributeValue(XfdfConstants.COLOR)));
        annotation.setDate(new PdfString(annotObject.getAttributeValue(XfdfConstants.DATE)));
        String name = annotObject.getAttributeValue(XfdfConstants.NAME);
        annotation.setName(new PdfString(name));
        annotMap.put(name, annotation);
        // add pending replies
        for(PdfMarkupAnnotation reply : replyMap.getOrDefault(name, Collections.emptyList())) {
            reply.setInReplyTo(annotation);
        }
        replyMap.remove(name);
        annotation.setTitle(new PdfString(annotObject.getAttributeValue(XfdfConstants.TITLE)));
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
        annotation.setCreationDate(new PdfString(annotObject.getAttributeValue(XfdfConstants.CREATION_DATE)));
        annotation.setSubject(new PdfString(annotObject.getAttributeValue(XfdfConstants.SUBJECT)));
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

        PdfString plainContents = annotation.getContents();
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

    private PdfFormXObject getCaretAppearance() {
        if(this.caretXObj != null) {
            return this.caretXObj;
        }
        // draw a caret on a 30x30 canvas
        this.caretXObj = new PdfFormXObject(new Rectangle(30, 30));
        PdfCanvas canvas = new PdfCanvas(this.caretXObj, this.pdfDocument);
        canvas.setFillColor(DeviceRgb.BLUE)
                .moveTo(15, 30)
                .curveTo(15, 30, 15, 0, 0, 0)
                .lineTo(30, 0)
                .curveTo(15, 0, 15,30, 15, 30)
                .closePath()
                .fill();
        return this.caretXObj;
    }

    private PdfFormXObject getCommentAppearance() {
        if(this.commentXObj != null) {
            return this.commentXObj;
        }
        // draw a speech bubble on a 30x30 canvas
        this.commentXObj = new PdfFormXObject(new Rectangle(30, 30));
        PdfCanvas canvas = new PdfCanvas(this.commentXObj, this.pdfDocument);

        canvas.setFillColorRgb(1, 1, 0)
                .setLineWidth(0.85f)
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

    private PdfTextMarkupAnnotation addTextMarkupAnnotationToPdf(PdfName subtype, AnnotObject annotObject) {
        Rectangle rect = readAnnotRect(annotObject);
        float[] quads = readAnnotQuadPoints(annotObject);
        PdfTextMarkupAnnotation pdfAnnot = new PdfTextMarkupAnnotation(rect, subtype, quads);

        addCommonAnnotationAttributes(pdfAnnot, annotObject);
        addMarkupAnnotationAttributes(pdfAnnot, annotObject);
        int page = readAnnotPage(annotObject);
        pdfDocument.getPage(page).addAnnotation(pdfAnnot);
        addPopupAnnotation(page, pdfAnnot, annotObject.getPopup());
        return pdfAnnot;
    }

    private void addAnnotationToPdf(AnnotObject annotObject) {
        String annotName = annotObject.getName();
        int page;
        if (annotName != null) {
            switch (annotName) {
                case XfdfConstants.TEXT:
                    PdfTextAnnotation pdfTextAnnotation = new PdfTextAnnotation(readAnnotRect(annotObject));
                    addCommonAnnotationAttributes(pdfTextAnnotation, annotObject);
                    addMarkupAnnotationAttributes(pdfTextAnnotation, annotObject);

                    String icon = annotObject.getAttributeValue(XfdfConstants.ICON);
                    if("Comment".equals(icon)) {
                        pdfTextAnnotation.setNormalAppearance(this.getCommentAppearance().getPdfObject());
                    }
                    pdfTextAnnotation.setIconName(new PdfName(icon));
                    if(annotObject.getAttributeValue(XfdfConstants.STATE) != null) {
                        pdfTextAnnotation.setState(new PdfString(annotObject.getAttributeValue(XfdfConstants.STATE)));
                    }
                    if(annotObject.getAttributeValue(XfdfConstants.STATE_MODEL) != null) {
                        pdfTextAnnotation.setStateModel(new PdfString(annotObject.getAttributeValue(XfdfConstants.STATE_MODEL)));
                    }

                    page = readAnnotPage(annotObject);
                    pdfDocument.getPage(page).addAnnotation(pdfTextAnnotation);
                    addPopupAnnotation(page, pdfTextAnnotation, annotObject.getPopup());
                    break;
                case XfdfConstants.HIGHLIGHT:
                    addTextMarkupAnnotationToPdf(PdfName.Highlight, annotObject)
                            .setColor(new DeviceRgb(1f, 1f, 0));
                    break;
                case XfdfConstants.UNDERLINE:
                    addTextMarkupAnnotationToPdf(PdfName.Underline, annotObject)
                            .setColor(DeviceRgb.RED);
                    break;
                case XfdfConstants.STRIKEOUT:
                    addTextMarkupAnnotationToPdf(PdfName.StrikeOut, annotObject)
                            .setColor(DeviceRgb.RED);
                    break;
                case XfdfConstants.SQUIGGLY:
                    addTextMarkupAnnotationToPdf(PdfName.Squiggly, annotObject)
                            .setColor(DeviceRgb.RED);
                    break;
                case XfdfConstants.CARET:
                    PdfCaretAnnotation caretAnnotation = new PdfCaretAnnotation(readAnnotRect(annotObject));
                    caretAnnotation.setNormalAppearance(this.getCaretAppearance().getPdfObject());
                    addCommonAnnotationAttributes(caretAnnotation, annotObject);
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
