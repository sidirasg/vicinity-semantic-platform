package sk.intersoft.vicinity.platform.semantic;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.intersoft.vicinity.platform.semantic.graph.Graph;
import sk.intersoft.vicinity.platform.semantic.lifting.AgoraSupport;
import sk.intersoft.vicinity.platform.semantic.lifting.ThingsLifter;
import sk.intersoft.vicinity.platform.semantic.lifting.model.ThingsLifterResult;
import sk.intersoft.vicinity.platform.semantic.lifting.model.thing.ThingDescription;
import sk.intersoft.vicinity.platform.semantic.ontology.NamespacePrefix;
import sk.intersoft.vicinity.platform.semantic.ontology.Namespaces;
import sk.intersoft.vicinity.platform.semantic.ontology.OntologyResource;
import sk.intersoft.vicinity.platform.semantic.sparql.SPARQL;
import sk.intersoft.vicinity.platform.semantic.utils.JSONUtil;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Thing2Ontology {
    Repository repository = Repository.getInstance();
    ValueFactory factory = SimpleValueFactory.getInstance();

    final static Logger logger = LoggerFactory.getLogger(Thing2Ontology.class.getName());

    private Set<String> extract(String query, String key)  {
        try{
            SPARQL sparql = new SPARQL();
            JSONObject result = sparql.query(query);
            List<JSONObject> bindings = JSONUtil.getObjectArray("bindings", result.getJSONObject("results"));
            Set<String> values = new HashSet<String>();
            for(JSONObject b : bindings) {
                JSONObject value = b.getJSONObject(key);
                String uri = JSONUtil.getString("value", value);
                values.add(Namespaces.toPrefixed(uri));
            }
            return values;
        }
        catch(Exception e){
            logger.error("EXCEPTION", e);
        }

        return new HashSet<String>();
    }

    public Set<String> getProperties()  {
        String query = "PREFIX rdfs: <"+Namespaces.nsToPrefixURI(Namespaces.rdfs)+"> " +
                "PREFIX ssn: <"+Namespaces.nsToPrefixURI(Namespaces.ssn)+"> " +
                "select ?x where {" +
                "?x rdf:type ssn:Property ." +
                "}";

        Set<String> result = extract(query, "x");
        logger.info("PROPERTIES QUERY: \n"+query);
        logger.info("PROPERTY INDIVIDUALS: \n" + result);

        return result;
    }
    public Set<String> getDeviceTypes()  {
        String query = "PREFIX : <"+Namespaces.nsToPrefixURI(Namespaces.core)+"> " +
                "PREFIX rdfs: <"+Namespaces.nsToPrefixURI(Namespaces.rdfs)+"> " +
                "select ?x where {" +
                "?x rdfs:subClassOf :Device ." +
                "}";

        Set<String> result = extract(query, "x");
        result.add(Namespaces.prefixed(NamespacePrefix.core, "Device"));
        logger.info("DEVICES QUERY: \n"+query);
        logger.info("DEVICE TYPES: \n"+result);

        return result;
    }
    public Set<String> getServiceTypes()  {
        String query = "PREFIX : <"+Namespaces.nsToPrefixURI(Namespaces.core)+"> " +
                "PREFIX rdfs: <"+Namespaces.nsToPrefixURI(Namespaces.rdfs)+"> " +
                "select ?x where {" +
                "?x rdfs:subClassOf :Service ." +
                "}";

        Set<String> result = extract(query, "x");
        result.add(Namespaces.prefixed(NamespacePrefix.core, "Service"));
        logger.info("SERVICES QUERY: \n"+query);
        logger.info("SERVICE TYPES: \n"+result);

        return result;
    }


    public void populate(JSONObject thing) throws Exception {
        RepositoryConnection connection = repository.getConnection();
        try{
            RDFParser rdfParser = Rio.createParser(RDFFormat.JSONLD);

            logger.info("THING POPULATION STUFF:");

            String contextURI = OntologyResource.thingInstanceURI(thing.getString(ThingDescription.OID_KEY));
            logger.info("CtX: "+contextURI);

            TreeModel graph = new TreeModel();
            rdfParser.setRDFHandler(new StatementCollector(graph));
            rdfParser.parse(new StringReader(thing.toString()), "");


            connection.begin();

            Iterator<Statement> i = graph.iterator();
            while(i.hasNext()) {
                Statement st = i.next();

//                logger.debug(
//                        st.getSubject() + " " +
//                                st.getPredicate() + " "+
//                                st.getObject());
                logger.debug("TRIPLE IN: " +
                        Namespaces.toPrefixed(st.getSubject().stringValue()) + " " +
                                Namespaces.toPrefixed(st.getPredicate().stringValue()) + " "+
                                Namespaces.toPrefixed(st.getObject().stringValue()));

                connection.add(st, factory.createIRI(contextURI));
            }
            logger.info("THING POPULATION STUFF: DONE");

            connection.commit();
        }
        catch(Exception e){
            logger.error("", e);
            connection.rollback();
        }
        finally {
            connection.close();
        }

    }

    public ThingsLifterResult validateAndLift(String data){
        logger.info("VALIDATING AND LIFTING THING DATA");


        ThingsLifter lifter = new ThingsLifter(getDeviceTypes(), getServiceTypes(), getProperties());
        ThingsLifterResult lifting = lifter.lift(data);

        logger.info("LIFTING: "+lifting.thing);
        if(lifting.thing != null){
            logger.info(lifting.thing.toString(2));
        }
        else{
            lifting.errors.add("Thing was not processed!");
        }
        logger.info("LIFTING ERRORS: "+lifting.errors.size());
        for(String error : lifting.errors) {
            logger.info("> "+error);
        }

        return lifting;
    }

    public JSONObject populate(String data, boolean updateContent)  {
        logger.info("POPULATING NEW THING: \n"+data);

        ThingsLifterResult lifting = validateAndLift(data);

        if(lifting.thing != null && lifting.errors.isEmpty()){
            if(updateContent){
                logger.info("UPDATING CONTENT .. DELETE FIRST");
                String oid = lifting.thing.getString(ThingDescription.OID_KEY);
                if(oid != null){
                    delete(oid);
                }
                else{
                    logger.info("UNABLE TO DELETE THING WITH UNKNOWN OID!");
                }


            }
            logger.info("POPULATING!");
            try{
                populate(lifting.thing);

                Ontology2Thing o2t = new Ontology2Thing();
                ThingDescription thing = o2t.toThing(lifting.thing.getString(ThingDescription.OID_KEY));

                logger.debug("THING FROM GRAPH: \n"+thing.toString(0));

                (new AgoraSupport(thing)).add();



                JSONObject thingJSON = o2t.toJSON(thing);

                JSONObject out = new JSONObject();
                out.put("lifting", thingJSON);
                return out;

            }
            catch(Exception e) {
                lifting.errors.add(e.getMessage());
                lifting.errors.add("something went ape during ontology population!");
                logger.error("EXCEPTION", e);
                delete(lifting.thing.getString(ThingDescription.OID_KEY));
            }
        }
        else {
            logger.info("NOT POPULATING!");

        }

        return lifting.failure();
    }


    public JSONObject create(String data)  {
        return populate(data, false);
    }

    public JSONObject update(String data)  {
        return populate(data, true);
    }

    public boolean contextExists(String contextURI, RepositoryConnection connection) throws Exception {
        logger.debug("checking if context exists: ["+contextURI+"]");
        RepositoryResult<Statement> result =
                connection.getStatements(null, null, null, true, factory.createIRI(contextURI));
        return result.hasNext();

    }



    public boolean delete(String oid) {

        try{

            logger.info("DELETING INSTANCE FOR: ["+oid+"]");
            String contextURI = OntologyResource.thingInstanceURI(oid);
            String uri = OntologyResource.thingInstanceURI(oid);

            logger.info("THING URI: ["+uri+"]");
            logger.info("CONTEXT URI: ["+contextURI+"]");

            Graph graph = repository.loadGraph(uri, contextURI);

            logger.debug("getting graph for OID: [" + oid + "]");

            if(graph != null) {
                logger.debug("graph exists: \n"+graph.describe());
                Set<String> contexts = graph.values(AgoraSupport.HAS_CONTEXT_GRAPH);

                String tedURI = AgoraSupport.getTEDInstance();

                logger.debug("deleting subgraphs: "+contexts);

                RepositoryConnection connection = repository.getConnection();
                try{

                    connection.begin();
                    for(String ctx: contexts){
                        logger.debug("deleting subgraph: "+ctx);
                        connection.clear(factory.createIRI(ctx));

                    }
                    logger.debug("deleting thing: "+contextURI);
                    connection.clear(factory.createIRI(contextURI));

                    if(tedURI == null){
                        logger.error("TED instance NOT found!");
                    }
                    else {
                        AgoraSupport.deleteTEDRelations(contextURI, contexts, tedURI, connection);
                    }


                    connection.commit();
                    return true;

                }
                catch(Exception e){
                    logger.error("EXCEPTION", e);
                }
                finally {
                    connection.close();
                }

            }


        }
        catch(Exception e){
            logger.error("EXCEPTION", e);
        }

        return false;
    }
}
