# Semantic storage

Semantic storage is implemented and deployed as semantic triplestore implementing
well known [RDF4J framework](http://rdf4j.org/).
RDF4J framework (former known as Sesame) is a powerful Java framework
for processing and handling RDF data. This includes creating, parsing, scalable storage,
reasoning and querying with RDF and Linked Data.
It offers an easy-to-use API that can be connected to all leading RDF database solutions.

RDF4J API serves also as definition of standard interface for managing RDF data.
This means, the different semantic databases - the triplestores - use to implement this API.
This means, the underlying semantic database technology can be easily replaced
without changing the code. The only requirement is, that semantic database
implements the RDF4J API.

In VICINITY, the [GraphDB](http://graphdb.ontotext.com/documentation/free/)
semantic database is currently used. Reasons why we used the GraphDB:
* It is the state of art semantic database, implementing the quantum of necessary the W3C standards.
* It natively contains the configurable reasoning mechanisms, enabling rich semantic search in stored
semantic data. Reasoning mechanism can be configured for custom semantic ruleset,
specifying the expressivity of the semantic language, the more expressive, the
 more query capabilities it provides (e.g. RDFS, OWL-Lite, OWL-Horst, OWL-Full, etc.).
* Nowadays it is one of the most scalable and the most fast triplestores available.
It is able to handle literally the billions of statements in real time (for CRUD and querying).
* It comes with intuitive workbench making the life of developer really easier.
* I had a chance to be in contact with GraphDB developers,
 when GraphDB was in quite early development stage (known as OWLIM that time) and I've tested its
 functionality (i was using it as the prototype in different project).
 This reason is not sentimental, this reason is common sense, because
 I know, that this guys are bloody professionals in semantics.
 So simply said, i don't know anything better available than GraphDB today!

GraphDB may run as the standalone server or can be bundled in the application.

