/********************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the W3C Software Notice and
 * Document License (2015-05-13) which is available at
 * https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document.
 *
 * SPDX-License-Identifier: EPL-2.0 OR W3C-20150513
 ********************************************************************************/
package org.eclipse.thingweb.directory.rdf

import groovy.util.logging.Log
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.LinkedHashModel
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.DC
import org.eclipse.rdf4j.model.vocabulary.DCTERMS
import org.eclipse.rdf4j.model.vocabulary.DCAT
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.RepositoryException
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.repository.util.Repositories
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.eclipse.thingweb.directory.Resource
import org.eclipse.thingweb.directory.ResourceManager

/**
 * .
 *
 * @author Victor Charpenay
 * @creation 06.08.2018
 *
 */
@Log
@Singleton
class RDFResourceManager extends ResourceManager {

	static final SPARQL_QUERY_ENDPOINT_PROPERTY = 'org.eclipse.rdf4j.repository.rdf.sparqlQueryEndpoint'

	/**
	 * defaults to query endpoint if not provided
	 */
	static final SPARQL_UPDATE_ENDPOINT_PROPERTY = 'org.eclipse.rdf4j.repository.rdf.sparqlUpdateEndpoint'

	static final SPARQL_USERNAME_PROPERTY = 'org.eclipse.rdf4j.repository.rdf.sparqlUsername'

	static final SPARQL_PASSWORD_PROPERTY = 'org.eclipse.rdf4j.repository.rdf.sparqlPassword'
	
	Repository repo
	
	private final ValueFactory vf = SimpleValueFactory.instance
	
	/**
	 * Every time the 'repo' attribute is accessed, an attempt is made to establish a connection
	 * to a SPARQL endpoint or an in-memory store, if no connection is available.
	 *
	 * @return the repository object
	 */
	Repository getRepo() {
		if (!repo) connect()
		return repo;
	}
	
	@Override
	protected void register(Resource res) {
		def rdf = res as RDFResource // TODO try serdes if not rdfresource
		def iri = rdf.iri

		// TODO check it does not already exist
		Repositories.consume(getRepo(), { RepositoryConnection con ->
			con.add(iri, RDF.TYPE, DCAT.DATASET)
			con.add(iri, DCTERMS.ISSUED, vf.createLiteral(new Date()))
			con.add(rdf.graph)
		})
	}
	
	@Override
	protected Resource get(String id) {
		def iri = vf.createIRI(id)
		
		def g = Repositories.get(getRepo(), { RepositoryConnection con ->
			def g = new LinkedHashModel()
			
			// content
			def q = "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <${id}> { ?s ?p ?o }}"
			def r = con.prepareGraphQuery(q).evaluate()
			while (r.hasNext()) {
				Statement st = r.next()
				g.add(st.subject, st.predicate, st.object, iri)
			}
			
			if (!g.isEmpty()) {
				// meta-data
				q = "CONSTRUCT { <${id}> ?p ?o } WHERE { <${id}> ?p ?o . FILTER NOT EXISTS { GRAPH <${id}> { ?s ?p ?o }}}"
				r = con.prepareGraphQuery(q).evaluate()
				while (r.hasNext()) {
					Statement st = r.next()
					g.add(st.subject, st.predicate, st.object)
				}
			}
			
			return g
		})
		
		return new RDFResource(g);
	}
	
	@Override
	protected void replace(Resource res, Resource other) {
		def iri = vf.createIRI(res.id)
		def rdf = other as RDFResource // TODO try serdes if not rdfresource
		
		// TODO check base, lt
		Repositories.consume(getRepo(), { RepositoryConnection con ->
			con.add(iri, DCTERMS.MODIFIED, vf.createLiteral(new Date()))
			con.clear(iri)
			con.add(rdf.graph.filter(null, null as IRI, null, rdf.iri), iri)
		})
	}
	
	@Override
	protected void delete(Resource res) {
		def iri = vf.createIRI(res.id)
		
		Repositories.consume(getRepo(), { RepositoryConnection con ->
			con.clear(iri)
		})
	}
	
	private def connect() {
		String queryEndpoint = System.getProperty(SPARQL_QUERY_ENDPOINT_PROPERTY)
		String updateEndpoint = System.getProperty(SPARQL_UPDATE_ENDPOINT_PROPERTY, queryEndpoint)
		
		if (queryEndpoint) {
			repo = new SPARQLRepository(queryEndpoint, updateEndpoint)
			
			String username = System.getProperty(SPARQL_USERNAME_PROPERTY)
			String password = System.getProperty(SPARQL_PASSWORD_PROPERTY)
			
			if (username && password) {
				(repo as SPARQLRepository).setUsernameAndPassword(username, password)
			} else if (username || password) {
				log.warning('Provided SPARQL endpoint credentials are incomplete')
			}
		
			try {
				// probe to test SPARQL endpoint availability
				// TODO request SPARQL service description and check for sd:UnionDefaultGraph
				repo.getConnection().isEmpty();
			} catch (RepositoryException e) {
				repo = null
				log.warning('SPARQL endpoint cannot be reached, switching to main memory RDF store...');
			}
		}
		
		// transient in-memory store
		if (!repo) repo = new SailRepository(new MemoryStore())
		
		repo.initialize()
	}

}