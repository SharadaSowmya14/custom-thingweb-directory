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
package org.eclipse.thingweb.directory.app;

import java.net.URL;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {

	public static void main(String[] args) throws Exception {
		Server server = new Server(8080);
		
		ClassLoader cl = Main.class.getClassLoader();
		URL filename = cl.getResource("directory-servlet-0.10.0.war");
		
		WebAppContext ctx = new WebAppContext();
		ctx.setContextPath("/");
		ctx.setWar(filename.getPath());
		
		server.setHandler(ctx);
		
		server.start();
		
		server.dumpStdErr();
		
		// TODO signal to stop server
		server.join();
	}
	
}
