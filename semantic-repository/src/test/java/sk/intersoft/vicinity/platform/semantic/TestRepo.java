package sk.intersoft.vicinity.platform.semantic;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.json.JSONObject;
import sk.intersoft.vicinity.platform.semantic.graph.Graph;
import sk.intersoft.vicinity.platform.semantic.sparql.SPARQL;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.*;

public class TestRepo {


    Repository repository = Repository.getInstance();

    public static String file2string(String path) {
        try{
            return new Scanner(new File(path)).useDelimiter("\\Z").next();
        }
        catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public void populate() throws Exception {

        JSONObject thing = new JSONObject(file2string(new File("").getAbsolutePath() + "/semantic-repository/src/test/resources/json/test.json"));

        RepositoryConnection connection = repository.getConnection();
        ValueFactory factory = SimpleValueFactory.getInstance();
        try{
            RDFParser rdfParser = Rio.createParser(RDFFormat.JSONLD);

            System.out.println("----------------------");
            System.out.println("THING STUFF:");

            String contextURI = "http://test.context#x";
            System.out.println("CtX: "+contextURI);

            TreeModel graph = new TreeModel();
            rdfParser.setRDFHandler(new StatementCollector(graph));
            rdfParser.parse(new StringReader(thing.toString()), "");

            Iterator<Statement> i = graph.iterator();
            while(i.hasNext()) {
                Statement st = i.next();

                System.out.println(
                        st.getSubject() + " " +
                                st.getPredicate() + " "+
                                st.getObject());
                System.out.println(
                        st.getSubject().stringValue() + " " +
                                st.getPredicate().stringValue() + " "+
                                st.getObject().stringValue());
                System.out.println("");

                connection.add(st, factory.createIRI(contextURI));
            }
            System.out.println("----------------------");

        }
        finally {
            connection.close();
        }

    }


    public void query() throws Exception {
        String q = "PREFIXx data:<http://examples.ontotext.com/family/data#>  SELECT * WHERE {data:John ?p ?o.}";
        SPARQL s=  new SPARQL();
        System.out.println(s.query(q).toString(2));
    }

    public void graph() throws Exception {
        Graph g = repository.loadGraph("http://vicinity.eu/data#xyz", "http://vicinity.eu/data#xyz");
        System.out.println(g.show());
        System.out.println(g.describe());
    }

    public void o2t() throws Exception {
        Ontology2Thing o = new Ontology2Thing();
        System.out.println(o.toJSON(o.toThing("814fb36d-beab-40a9-b4ba-4680ae5d67e5")).toString(2));
//        System.out.println(o.toJSON("test3").toString(2));
    }

    public void delete() throws Exception {
        Thing2Ontology handler = new Thing2Ontology();
        System.out.println(handler.delete("test3"));

    }

    public List<String> ctxs() throws Exception {
        List<String> ctxs = new ArrayList<>();
        try{
            RepositoryConnection connection = repository.getConnection();
            try{
                RepositoryResult<Resource> contexts = connection.getContextIDs();
                if(contexts.hasNext()){
                    while(contexts.hasNext()){
                        String r = contexts.next().stringValue();
                        if(r.contains("things")){
                            ctxs.add(r);
                        }
                    }
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
            finally {
                connection.close();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        Collections.shuffle(ctxs);
        return ctxs;
    }

    public void test(){
        Repository repo = Repository.getInstance();
        try{
            List<String> ctxs = ctxs();
            if(!ctxs.isEmpty()){

                String ctx = ctxs.get(0);
                System.out.println("selecting random context: "+ctx);
                Graph g = repo.loadGraph(ctx, ctx);
                System.out.println("G: "+g.size());
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws  Exception {
        TestRepo t = new TestRepo();
//        t.query();
//        t.graph();
//        t.delete();
//        t.o2t();
        t.test();
    }

}
