package org.dspace.app.webui.servlet;

import org.apache.log4j.Logger;
import org.dspace.app.webui.util.SoapHelper;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

/**
 * Created by root on 1/1/16.
 */
public class ImportServlet extends DSpaceServlet {

    /** Logger */
    private static Logger log = Logger.getLogger(EditProfileServlet.class);

    protected void doDSGet(Context context, HttpServletRequest request,
                           HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException {

        
        request.setAttribute("community_id", request.getParameter("community_id"));
        request.setAttribute("collection_id", request.getParameter("collection_id"));

        response.setCharacterEncoding("UTF-8");
        request.setCharacterEncoding("UTF-8");
        request.getRequestDispatcher("/import/import-home.jsp").forward(request, response);

    }

    protected void doDSPost(Context context, HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException{

        SoapHelper sh = new SoapHelper();

        String collectionId = request.getParameter("collection_id");

        Collection col = Collection.find(context, Integer.parseInt(collectionId));

        request.setAttribute("collection_id", collectionId);

        WorkspaceItem wsitem = WorkspaceItem.createMass(context, col, false);
        Item itemItem = wsitem.getItem();
        //response.getWriter().write("test");

        itemItem.setOwningCollection(col);
        HandleManager.createHandle(context, itemItem);

        String test = request.getParameter("action");
        String item_id = request.getParameter("import_item");

        if(test != null) {
            Document docMeta = null;


            if (test.equals("write_ident")) {
                String iden = request.getParameter("identifier");
                docMeta = sh.getRecordById(iden);
                createItem(docMeta, itemItem, request, context, col, wsitem);
                request.getRequestDispatcher("/import/import-sucess.jsp").forward(request, response);
            }

            if (test.equals("write_name")) {
                Document bullshit_doc;
                bullshit_doc = sh.getRecordByCode(item_id);
                NodeList idNode = bullshit_doc.getElementsByTagName("m:BiblId");

                String idItem = idNode.item(0).getTextContent();

                //request.setAttribute(idItem, "identifier");
                docMeta = sh.getRecordById(idItem);
                createItem(docMeta, itemItem, request, context, col, wsitem);
                request.getRequestDispatcher("/import/import-sucess.jsp").forward(request, response);
            }
        }

        if(test == null) {
            String uuid = request.getParameter("uuid_search");
            Document doc = sh.getRecordById(uuid);


            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = null;
            try {
                transformer = tf.newTransformer();
            } catch (TransformerConfigurationException e) {
                e.printStackTrace();
            }
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            try {
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
            } catch (TransformerException e) {
                e.printStackTrace();
            }
            String output = writer.getBuffer().toString().replaceAll("\n|\r", "");


            request.setAttribute("community_id", request.getParameter("community_id"));
            request.setAttribute("collection_id", request.getParameter("collection_id"));
            request.setAttribute("uuid_search", uuid);
            request.setAttribute("document", doc);



            NodeList testWow = doc.getElementsByTagName("Records");
            try{
                if(testWow.getLength() > 0) {
                    request.getRequestDispatcher("/import/import-item.jsp").forward(request, response);
                } else {
                    request.getRequestDispatcher("/import/import-no.jsp").forward(request, response);
                }
            } catch (Exception e){
                request.getRequestDispatcher("/import/import-no.jsp").forward(request, response);
            }

            }
            else {
            String name = request.getParameter("name");
            String title = request.getParameter("title");

            if(name.equals(""))
                name = null;

            if(title.equals(""))
                title = null;

            Document doc = sh.getRecordByName(name, title);


            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = null;
            try {
                transformer = tf.newTransformer();
            } catch (TransformerConfigurationException e) {
                e.printStackTrace();
            }
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            try {
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
            } catch (TransformerException e) {
                e.printStackTrace();
            }
            String output = writer.getBuffer().toString().replaceAll("\n|\r", "");


            request.setAttribute("community_id", request.getParameter("community_id"));
            request.setAttribute("collection_id", request.getParameter("collection_id"));
            request.setAttribute("document", doc);

            NodeList testWow = doc.getElementsByTagName("m:BiblRecords");
            try{
                if(testWow.getLength() > 0) {
                    request.getRequestDispatcher("/import/import-items.jsp").forward(request, response);
                } else {
                    request.getRequestDispatcher("/import/import-no.jsp").forward(request, response);
                }
            } catch (Exception e){
                request.getRequestDispatcher("/import/import-no.jsp").forward(request, response);
            }

        }
    }

    private void createItem(Document docMeta, Item ti, HttpServletRequest request, Context context, Collection col, WorkspaceItem wsitem) throws SQLException, AuthorizeException {

        XPathFactory xpathFactory = XPathFactory.newInstance();

        // Create XPath object
        XPath xpath = xpathFactory.newXPath();


        //Node nodeValue = nodeTitle.getChildNodes().item(3);

        XPathExpression expr =
                null;

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Subject']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList subject = null;
        try {
            subject = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        writeMetaDataToItemLowerCaseSubject(ti, "subject", subject);


        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Creator']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        NodeList creators = null;
        try {
            creators = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        try {
            Node creator = creators.item(0);

            String creatorsString = creator.getTextContent();
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "creator", null, "ru", creatorsString);
            String[] creatorsAr = creatorsString.split(",");
            for(int i = 0; i<creatorsAr.length; i++){
                ti.addMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", "ru", creatorsAr[i]);
            }
        } catch (Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Date']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList dates = null;
        try {
            dates = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "date", "issued", "ru", dates.item(0).getTextContent());
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Identifier']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList idents = null;
        try {
            idents = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        writeMetaDataToItemLowerCaseIdentifier(ti, "identifier", idents, request);

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Title']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList nodes = null;
        try {
            nodes = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            writeMetaDataToItemLowerCaseTitle(ti, "title", nodes);
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Description']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList descrs = null;
        try {
            descrs = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        writeMetaDataToItemLowerCaseDescr(ti, "description", descrs);

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Language']");
        } catch (XPathExpressionException e) {
            log.error("lang error:", e);
        }



        NodeList langs = null;
        try {
            langs = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            log.error("lang error:", e);
        }

        writeMetaDataToItemLowerCaseLang(ti, "language", langs, request);



        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Coverage']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList cover = null;
        try {
            cover = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        writeMetaDataToItemLowerCase(ti, "coverage", cover);


        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Source']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList sources = null;
        try {
            sources = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "source", null, "ru", sources.item(0).getTextContent());
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Type']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList type = null;
        try {
            type = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "type", null, "ru", type.item(0).getTextContent());
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Rights']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList rughts = null;
        try {
            rughts = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "rights", null, "ru", rughts.item(0).getTextContent());
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Publisher']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList publisher = null;
        try {
            publisher = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "publisher", null, "ru", publisher.item(0).getTextContent());
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Contributor']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList contributor = null;
        try {
            contributor = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        try {
            String creatorsString = contributor.item(0).getTextContent();
            String[] creatorsAr = creatorsString.split(",");

            for(int i = 0; i<creatorsAr.length; i++){
                ti.addMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", "ru", creatorsAr[i]);
            }
        } catch(Exception e){

        }

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Citation']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList citation = null;
        try {
            citation = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        writeMetaDataToItemLowerCase(ti, "citation", citation);

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Format']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList format = null;
        try {
            format = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        writeMetaDataToItemLowerCase(ti, "format", format);

        try {
            expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Relation']");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }



        NodeList relation = null;
        try {
            relation = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        writeMetaDataToItemLowerCase(ti, "relation", relation);

        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "date", "accessioned", "ru", new Date().toString());
        }
        catch(Exception e1){

        }
        try {
            ti.addMetadata(MetadataSchema.DC_SCHEMA, "date", "available", "ru", new Date().toString());
        } catch(Exception e2){

        }

        ti.setDiscoverable(true);

        //itemItem.update();



        try {
            try {
                expr = xpath.compile("/*/*/*/*/*[local-name()='Records']/*[local-name()='Link']");
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }



            NodeList linkList = null;
            try {
                linkList = (NodeList) expr.evaluate(docMeta, XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            Node link = linkList.item(0);

            String firstUrl = "http://lib.ssau.ru/download?fname=";

            String linkEncode = URLEncoder.encode(link.getTextContent(), "UTF-8");

            String filenamelel = link.getTextContent().substring(link.getTextContent().lastIndexOf('\\') + 1);

            InputStream iss  = new URL(firstUrl+linkEncode).openStream();

            log.info("wowlol: "+firstUrl+linkEncode);

            ti.createBundle("ORIGINAL");
            Bitstream b = ti.getBundles("ORIGINAL")[0].createBitstream(iss);
            b.setName(filenamelel);
            b.setDescription("from 1C");
            b.setSource("1C");

            ti.getBundles("ORIGINAL")[0].setPrimaryBitstreamID(b.getID());




            BitstreamFormat bf = null;

            bf = FormatIdentifier.guessFormat(context, b);
            b.setFormat(bf);

            b.update();
            ti.update();

            iss.close();
        } catch (Exception e) {
            log.error("wtferror", e);
        }




        // Group groups = Group.findByName(context, "Anonymous");
        TableRow row = DatabaseManager.row("collection2item");


        PreparedStatement statement = null;
        //      ResultSet rs = null;
        statement = context.getDBConnection().prepareStatement("DELETE FROM workspaceitem WHERE workspace_item_id="+wsitem.getID());
        int ij = statement.executeUpdate();

        row.setColumn("collection_id", col.getID());
        row.setColumn("item_id", ti.getID());



        DatabaseManager.insert(context, row);

        ti.inheritCollectionDefaultPolicies(col);

        ti.setArchived(true);

        ti.update();
        context.commit();

        try {
            String link = ti.getHandle();
            request.setAttribute("link", HandleManager.getCanonicalForm(link));
        } catch(Exception e){

        }
    }

    public void writeMetaDataToItemLowerCase(Item item, String qualifier, NodeList nodes){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            String qualtext = qulSubject.getTextContent().toLowerCase();

            item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());

        }
    }

    public void writeMetaDataToItemLowerCaseSubject(Item item,  String qualifier, NodeList nodes){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            if(qulSubject.getTextContent().toLowerCase().equals("subject")){
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
            }else {
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
            }
        }
    }

    public void writeMetaDataToItemLowerCaseIdentifier(Item item,  String qualifier, NodeList nodes, HttpServletRequest request){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            if(qulSubject.getTextContent().toLowerCase().equals("identifier")){
               // request.setAttribute("identifier", textSubject.getTextContent());
                //item.addMetadata(MetadataSchema.DC_SCHEMA, "subject", "lcc", "ru", textSubject.getTextContent());
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
            }else {
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
            }
        }
    }

    public void writeMetaDataToItemLowerCaseTitle(Item item,  String qualifier, NodeList nodes){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            if(qulSubject.getTextContent().toLowerCase().equals("title")){
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
            }else {
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
            }
        }
    }

    public void writeMetaDataToItemLowerCaseDescr(Item item,  String qualifier, NodeList nodes){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            if(qulSubject.getTextContent().toLowerCase().equals("abstract")){
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, "abstract", "ru", textSubject.getTextContent());
            }
        }
    }

    public void writeMetaDataToItemLowerCaseLang(Item item,  String qualifier, NodeList nodes, HttpServletRequest request){
        for(int j = 0; j < nodes.getLength(); j++){
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);

            request.setAttribute("wtf_lang", textSubject.getTextContent());
            item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
            //item.addMetadata(MetadataSchema.DC_SCHEMA, "subject", "lcsh", "ru", textSubject.getTextContent());
        }
    }


}