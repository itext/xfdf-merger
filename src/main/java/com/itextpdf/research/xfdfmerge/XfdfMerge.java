package com.itextpdf.research.xfdfmerge;

import com.itextpdf.commons.utils.MessageFormatUtil;
import com.itextpdf.forms.xfdf.AnnotObject;
import com.itextpdf.forms.xfdf.AnnotsObject;
import com.itextpdf.forms.xfdf.XfdfAnnotFactory;
import com.itextpdf.forms.xfdf.XfdfConstants;
import com.itextpdf.forms.xfdf.XfdfObject;
import com.itextpdf.forms.xfdf.XfdfObjectReadingUtils;
import com.itextpdf.io.logs.IoLogMessageConstant;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XfdfMerge {

    private static final Logger LOGGER = LoggerFactory.getLogger(XfdfMerge.class);
    private final Map<String, PdfAnnotation> annotMap = new HashMap<>();
    private final Map<String, List<PdfMarkupAnnotation>> replyMap = new HashMap<>();

    void mergeXfdfIntoPdf(XfdfObject xfdfObject, PdfDocument pdfDocument) {
        mergeAnnotations(xfdfObject.getAnnots(), pdfDocument);
    }

    /**
     * Merges existing XfdfObject into pdf document associated with it.
     *
     * @param annotsObject    The AnnotsObject with children AnnotObject entities to be mapped into PdfAnnotations.
     * @param pdfDocument     The associated pdf document.
     */
    private void mergeAnnotations(AnnotsObject annotsObject, PdfDocument pdfDocument) {
        List<AnnotObject> annotList = null;
        if (annotsObject != null) {
            annotList = annotsObject.getAnnotsList();
        }

        if (annotList != null && !annotList.isEmpty()) {
            for (AnnotObject annot : annotList) {
                addAnnotationToPdf(annot, pdfDocument);
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

    private void addPopupAnnotation(int page, PdfMarkupAnnotation parent, AnnotObject popup, PdfDocument pdfDoc) {
        if(popup != null) {
            PdfPopupAnnotation pdfPopupAnnot = new PdfPopupAnnotation(readAnnotRect(popup));
            // TODO set Open based on value in XFDF
            pdfPopupAnnot.setOpen(false)
                    .setFlags(XfdfObjectReadingUtils.convertFlagsFromString(popup.getAttributeValue(XfdfConstants.FLAGS)));
            parent.setPopup(pdfPopupAnnot);
            pdfDoc.getPage(page).addAnnotation(pdfPopupAnnot);
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
            // TODO make it so that the order doesn't matter?
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
            annotation.setRichText(rc);
        }
    }

    private Rectangle readAnnotRect(AnnotObject annotObject) {
        // TODO support transformations
        return XfdfObjectReadingUtils.convertRectFromString(annotObject.getAttributeValue(XfdfConstants.RECT));
    }

    private float[] readAnnotQuadPoints(AnnotObject annotObject) {
        // TODO support transformations
        return XfdfObjectReadingUtils.convertQuadPointsFromCoordsString(annotObject.getAttributeValue(XfdfConstants.COORDS));
    }

    private int readAnnotPage(AnnotObject annotObject) {
        // TODO support transformations
        return 1 + Integer.parseInt(annotObject.getAttribute(XfdfConstants.PAGE).getValue());
    }


    private void addTextMarkupAnnotationToPdf(PdfName subtype, AnnotObject annotObject, PdfDocument pdfDocument) {
        Rectangle rect = readAnnotRect(annotObject);
        float[] quads = readAnnotQuadPoints(annotObject);
        PdfTextMarkupAnnotation pdfAnnot = new PdfTextMarkupAnnotation(rect, subtype, quads);

        addCommonAnnotationAttributes(pdfAnnot, annotObject);
        addMarkupAnnotationAttributes(pdfAnnot, annotObject);
        int page = readAnnotPage(annotObject);
        pdfDocument.getPage(page).addAnnotation(pdfAnnot);
        addPopupAnnotation(page, pdfAnnot, annotObject.getPopup(), pdfDocument);
    }

    private void addAnnotationToPdf(AnnotObject annotObject, PdfDocument pdfDocument) {
        String annotName = annotObject.getName();
        int page;
        if (annotName != null) {
            switch (annotName) {
                //TODO DEVSIX-4027 add all attributes properly one by one
                case XfdfConstants.TEXT:
                    PdfTextAnnotation pdfTextAnnotation = new PdfTextAnnotation(readAnnotRect(annotObject));
                    addCommonAnnotationAttributes(pdfTextAnnotation, annotObject);
                    addMarkupAnnotationAttributes(pdfTextAnnotation, annotObject);

                    pdfTextAnnotation.setIconName(new PdfName(annotObject.getAttributeValue(XfdfConstants.ICON)));
                    if(annotObject.getAttributeValue(XfdfConstants.STATE) != null) {
                        pdfTextAnnotation.setState(new PdfString(annotObject.getAttributeValue(XfdfConstants.STATE)));
                    }
                    if(annotObject.getAttributeValue(XfdfConstants.STATE_MODEL) != null) {
                        pdfTextAnnotation.setStateModel(new PdfString(annotObject.getAttributeValue(XfdfConstants.STATE_MODEL)));
                    }

                    page = readAnnotPage(annotObject);
                    pdfDocument.getPage(page).addAnnotation(pdfTextAnnotation);
                    addPopupAnnotation(page, pdfTextAnnotation, annotObject.getPopup(), pdfDocument);
                    break;
                case XfdfConstants.HIGHLIGHT:
                    addTextMarkupAnnotationToPdf(PdfName.Highlight, annotObject, pdfDocument);
                    break;
                case XfdfConstants.UNDERLINE:
                    addTextMarkupAnnotationToPdf(PdfName.Underline, annotObject, pdfDocument);
                    break;
                case XfdfConstants.STRIKEOUT:
                    addTextMarkupAnnotationToPdf(PdfName.StrikeOut, annotObject, pdfDocument);
                    break;
                case XfdfConstants.SQUIGGLY:
                    addTextMarkupAnnotationToPdf(PdfName.Squiggly, annotObject, pdfDocument);
                    break;
                case XfdfConstants.CARET:
                    PdfCaretAnnotation caretAnnotation = new PdfCaretAnnotation(readAnnotRect(annotObject));
                    addCommonAnnotationAttributes(caretAnnotation, annotObject);
                    addMarkupAnnotationAttributes(caretAnnotation, annotObject);
                    page = readAnnotPage(annotObject);
                    pdfDocument.getPage(page).addAnnotation(caretAnnotation);
                    addPopupAnnotation(page, caretAnnotation, annotObject.getPopup(), pdfDocument);
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

    public static void main(String[] args) throws Exception {
        if(args.length != 3) {
            System.err.println("Usage: XfdfMerge input.pdf input.xfdf output.pdf");
            return;
        }
        String pdfIn = args[0];
        String xfdfIn = args[1];
        String pdfOut = args[2];

        XfdfObject xfdfRoot;
        LOGGER.info("Reading XFDF data from " + xfdfIn);
        try(InputStream is = new FileInputStream(xfdfIn)) {
            xfdfRoot = new XfdfAnnotFactory().createXfdfObject(is);
        }
        StampingProperties sp = new StampingProperties().useAppendMode();
        try(PdfReader r = new PdfReader(pdfIn);
            PdfWriter w = new PdfWriter(pdfOut);
            PdfDocument pdfDoc = new PdfDocument(r, w, sp)) {
            XfdfMerge mrg = new XfdfMerge();
            mrg.mergeXfdfIntoPdf(xfdfRoot, pdfDoc);
            LOGGER.info("Merged");
        }
    }
}
