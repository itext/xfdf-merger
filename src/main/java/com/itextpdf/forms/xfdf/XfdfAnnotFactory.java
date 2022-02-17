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

import com.itextpdf.commons.utils.MessageFormatUtil;
import com.itextpdf.io.logs.IoLogMessageConstant;
import com.itextpdf.kernel.pdf.PdfString;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

public class XfdfAnnotFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(XfdfAnnotFactory.class);
    public static final String UNSUPPORTED_ANNOT_ATTR = IoLogMessageConstant.XFDF_UNSUPPORTED_ANNOTATION_ATTRIBUTE + " \"{0}\"";

    /**
     * Extracts data from input stream into XfdfObject. Typically input stream is based on .xfdf file
     *
     * @param xfdfInputStream The input stream containing xml-styled xfdf data.
     * @return XfdfObject containing original xfdf data.
     */
    public XfdfObject createXfdfObject(InputStream xfdfInputStream) {
        XfdfObject xfdfObject = new XfdfObject();

        Document document = XfdfFileUtils.createXfdfDocumentFromStream(xfdfInputStream);

        Element root = document.getDocumentElement();
        List<AttributeObject> xfdfRootAttributes = readXfdfRootAttributes(root);
        xfdfObject.setAttributes(xfdfRootAttributes);

        NodeList nodeList = root.getChildNodes();

        visitChildNodes(nodeList, xfdfObject);

        return xfdfObject;
    }

    private void visitFNode(Node node, XfdfObject xfdfObject) {
        Node href = node.getAttributes().getNamedItem(XfdfConstants.HREF);
        if (href != null) {
            xfdfObject.setF(new FObject(href.getNodeValue()));
        } else {
            LOGGER.info(XfdfConstants.EMPTY_F_LEMENT);
        }
    }

    private void visitIdsNode(Node node, XfdfObject xfdfObject) {
        IdsObject idsObject = new IdsObject();
        Node original = node.getAttributes().getNamedItem(XfdfConstants.ORIGINAL);
        if (original != null) {
            idsObject.setOriginal(original.getNodeValue());
        }
        Node modified = node.getAttributes().getNamedItem(XfdfConstants.MODIFIED);
        if (modified != null) {
            idsObject.setModified(modified.getNodeValue());
        }
        xfdfObject.setIds(idsObject);
    }

    private void visitElementNode(Node node, XfdfObject xfdfObject) {
        if (XfdfConstants.FIELDS.equalsIgnoreCase(node.getNodeName())) {
            FieldsObject fieldsObject = new FieldsObject();
            readFieldList(node, fieldsObject);
            xfdfObject.setFields(fieldsObject);
        }
        if (XfdfConstants.F.equalsIgnoreCase(node.getNodeName())) {
            visitFNode(node, xfdfObject);
        }
        if (XfdfConstants.IDS.equalsIgnoreCase(node.getNodeName())) {
            visitIdsNode(node, xfdfObject);
        }
        if (XfdfConstants.ANNOTS.equalsIgnoreCase(node.getNodeName())) {
            AnnotsObject annotsObject = new AnnotsObject();
            readAnnotsList(node, annotsObject);
            xfdfObject.setAnnots(annotsObject);
        }
    }

    private void visitChildNodes(NodeList nList, XfdfObject xfdfObject) {
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                visitElementNode(node, xfdfObject);
            }
        }
    }

    private static boolean isAnnotSupported(String nodeName) {
        return XfdfConstants.TEXT.equalsIgnoreCase(nodeName) ||
                XfdfConstants.HIGHLIGHT.equalsIgnoreCase(nodeName) ||
                XfdfConstants.UNDERLINE.equalsIgnoreCase(nodeName) ||
                XfdfConstants.STRIKEOUT.equalsIgnoreCase(nodeName) ||
                XfdfConstants.SQUIGGLY.equalsIgnoreCase(nodeName) ||
                XfdfConstants.CARET.equalsIgnoreCase(nodeName) ||
                XfdfConstants.LINE.equalsIgnoreCase(nodeName);
    }

    private void readAnnotsList(Node node, AnnotsObject annotsObject) {
        NodeList annotsNodeList = node.getChildNodes();

        for (int temp = 0; temp < annotsNodeList.getLength(); temp++) {
            Node currentNode = annotsNodeList.item(temp);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE &&
                    isAnnotationSubtype(currentNode.getNodeName()) &&
                    isAnnotSupported(currentNode.getNodeName())) {
                visitAnnotationNode(currentNode, annotsObject);
            }
        }
    }

    private void visitAnnotationNode(Node currentNode, AnnotsObject annotsObject) {
        AnnotObject annotObject = new AnnotObject();
        annotObject.setName(currentNode.getNodeName());
        NamedNodeMap attributes = currentNode.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            addAnnotObjectAttribute(annotObject, attributes.item(i));
        }
        visitAnnotationInnerNodes(annotObject, currentNode);
        annotsObject.addAnnot(annotObject);
    }

    private void visitAnnotationInnerNodes(AnnotObject annotObject, Node annotNode) {
        NodeList children = annotNode.getChildNodes();

        for (int temp = 0; temp < children.getLength(); temp++) {
            Node node = children.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (XfdfConstants.CONTENTS.equalsIgnoreCase(node.getNodeName())) {
                    visitContentsSubelement(node, annotObject);
                }
                if (XfdfConstants.CONTENTS_RICHTEXT.equalsIgnoreCase(node.getNodeName())) {
                    visitContentsRichTextSubelement(node, annotObject);
                }
                if (XfdfConstants.POPUP.equalsIgnoreCase(node.getNodeName())) {
                    visitPopupSubelement(node, annotObject);
                }
                if (XfdfConstants.VERTICES.equalsIgnoreCase(node.getNodeName())) {
                    visitVerticesSubelement(node, annotObject);
                }
            }
        }
    }

    private void visitPopupSubelement(Node popupNode, AnnotObject annotObject) {
        //nothing inside
        //attr list : color, date, flags, name, rect (required), title. open
        AnnotObject popupAnnotObject = new AnnotObject();
        NamedNodeMap attributes = popupNode.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            addAnnotObjectAttribute(popupAnnotObject, attributes.item(i));
        }
        annotObject.setPopup(popupAnnotObject);
    }

    private void visitContentsSubelement(Node parentNode, AnnotObject annotObject) {
        //no attributes. inside a text string
        NodeList children = parentNode.getChildNodes();
        for (int temp = 0; temp < children.getLength(); temp++) {
            Node node = children.item(temp);
            if (node.getNodeType() == Node.TEXT_NODE) {
                annotObject.setContents(new PdfString(node.getNodeValue()));
            }
        }
    }

    private void visitContentsRichTextSubelement(Node parentNode, AnnotObject annotObject) {
        NodeList children = parentNode.getChildNodes();
        // set contents based on the node text if it isn't set yet, as a fallback
        PdfString contents = annotObject.getContents();
        if(contents == null || contents.toString().isBlank()) {
            annotObject.setContents(new PdfString(parentNode.getTextContent()));
        }
        // For the RC field, we want the actual XML content
        LSSerializer ser = ((DOMImplementationLS) parentNode.getOwnerDocument()
                        .getImplementation()
                        .getFeature("LS", "3.0")).createLSSerializer();
        DOMConfiguration domConfig = ser.getDomConfig();
        // disable XML declaration in the header (not necessary for RC in annotations)
        domConfig.setParameter("xml-declaration", false);
        // Serialise all the children
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            sb.append(ser.writeToString(children.item(i)));
        }
        annotObject.setContentsRichText(new PdfString(sb.toString()));
    }

    private void visitVerticesSubelement(Node parentNode, AnnotObject annotObject) {
        //no attributes, inside a text string
        NodeList children = parentNode.getChildNodes();
        for (int temp = 0; temp < children.getLength(); temp++) {
            Node node = children.item(temp);
            if (node.getNodeType() == Node.TEXT_NODE) {
                annotObject.setVertices(node.getNodeValue());
            }
        }
    }

    private void addAnnotObjectAttribute(AnnotObject annotObject, Node attributeNode) {
        if (attributeNode != null) {
            String attributeName = attributeNode.getNodeName();
            switch (attributeName) {
                case XfdfConstants.PAGE:
                    //required
                    annotObject.addFdfAttributes(Integer.parseInt(attributeNode.getNodeValue()));
                    break;
                case XfdfConstants.COLOR:
                case XfdfConstants.DATE:
                case XfdfConstants.FLAGS:
                case XfdfConstants.NAME:
                case XfdfConstants.RECT://required
                case XfdfConstants.TITLE:

                case XfdfConstants.CREATION_DATE:
                case XfdfConstants.OPACITY:
                case XfdfConstants.SUBJECT:
                case "IT": // not in XfdfConstants
                case XfdfConstants.ICON:
                case XfdfConstants.STATE:
                case XfdfConstants.STATE_MODEL:
                case XfdfConstants.IN_REPLY_TO:
                case XfdfConstants.REPLY_TYPE:
                case XfdfConstants.OPEN:
                case XfdfConstants.COORDS:
                case XfdfConstants.FRINGE:
                    annotObject.addAttribute(new AttributeObject(attributeName, attributeNode.getNodeValue()));
                    break;
                default: LOGGER.warn(MessageFormatUtil.format(UNSUPPORTED_ANNOT_ATTR, attributeName));
                    break;
            }
        }
    }

    private boolean isAnnotationSubtype(String tag) {
        return XfdfConstants.TEXT.equalsIgnoreCase(tag) ||
                XfdfConstants.HIGHLIGHT.equalsIgnoreCase(tag) ||
                XfdfConstants.UNDERLINE.equalsIgnoreCase(tag) ||
                XfdfConstants.STRIKEOUT.equalsIgnoreCase(tag) ||
                XfdfConstants.SQUIGGLY.equalsIgnoreCase(tag) ||
                XfdfConstants.LINE.equalsIgnoreCase(tag) ||
                XfdfConstants.CIRCLE.equalsIgnoreCase(tag) ||
                XfdfConstants.SQUARE.equalsIgnoreCase(tag) ||
                XfdfConstants.CARET.equalsIgnoreCase(tag) ||
                XfdfConstants.POLYGON.equalsIgnoreCase(tag) ||
                XfdfConstants.POLYLINE.equalsIgnoreCase(tag) ||
                XfdfConstants.STAMP.equalsIgnoreCase(tag) ||
                XfdfConstants.INK.equalsIgnoreCase(tag) ||
                XfdfConstants.FREETEXT.equalsIgnoreCase(tag) ||
                XfdfConstants.FILEATTACHMENT.equalsIgnoreCase(tag) ||
                XfdfConstants.SOUND.equalsIgnoreCase(tag) ||
                XfdfConstants.LINK.equalsIgnoreCase(tag) ||
                XfdfConstants.REDACT.equalsIgnoreCase(tag) ||
                XfdfConstants.PROJECTION.equalsIgnoreCase(tag);
        //projection annotation is not supported in iText
    }

    private void readFieldList(Node node, FieldsObject fieldsObject) {
        NodeList fieldNodeList = node.getChildNodes();

        for (int temp = 0; temp < fieldNodeList.getLength(); temp++) {
            Node currentNode = fieldNodeList.item(temp);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE && XfdfConstants.FIELD.equalsIgnoreCase(currentNode.getNodeName())) {
                FieldObject fieldObject = new FieldObject();
                visitInnerFields(fieldObject, currentNode, fieldsObject);
            }
        }
    }

    private void visitFieldElementNode(Node node, FieldObject parentField, FieldsObject fieldsObject) {
        if (XfdfConstants.VALUE.equalsIgnoreCase(node.getNodeName())) {
            Node valueTextNode = node.getFirstChild();
            if (valueTextNode != null) {
                parentField.setValue(valueTextNode.getTextContent());
            } else {
                LOGGER.info(XfdfConstants.EMPTY_FIELD_VALUE_ELEMENT);
            }
            return;
        }
        if (XfdfConstants.FIELD.equalsIgnoreCase(node.getNodeName())) {
            FieldObject childField = new FieldObject();
            childField.setParent(parentField);
            childField.setName(parentField.getName() + "." + node.getAttributes().item(0).getNodeValue());
            visitInnerFields(childField, node, fieldsObject);
            fieldsObject.addField(childField);
        }
    }

    private void visitInnerFields(FieldObject parentField, Node parentNode, FieldsObject fieldsObject) {
        if (parentNode.getAttributes().getLength() != 0) {
            if (parentField.getName() == null) {
                parentField.setName(parentNode.getAttributes().item(0).getNodeValue());
            }
        } else {
            LOGGER.info(XfdfConstants.EMPTY_FIELD_NAME_ELEMENT);
        }

        NodeList children = parentNode.getChildNodes();

        for (int temp = 0; temp < children.getLength(); temp++) {
            Node node = children.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                visitFieldElementNode(node, parentField, fieldsObject);
            }
        }
        fieldsObject.addField(parentField);
    }


    private List<AttributeObject> readXfdfRootAttributes(Element root) {
        NamedNodeMap attributes = root.getAttributes();
        int length = attributes.getLength();
        List<AttributeObject> attributeObjects = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            Node attributeNode = attributes.item(i);
            attributeObjects.add(new AttributeObject(attributeNode.getNodeName(), attributeNode.getNodeValue()));
        }
        return attributeObjects;
    }
}
