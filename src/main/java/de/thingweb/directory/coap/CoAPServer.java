package de.thingweb.directory.coap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

import de.thingweb.directory.rest.IndexResource;
import de.thingweb.directory.rest.RESTResource;
import de.thingweb.directory.rest.RESTServerInstance;

public class CoAPServer implements RESTServerInstance {

	protected IndexResource root;
	protected CoapServer server;
	protected Thread t;
	
	private int port;

  public CoAPServer(int port)
  {
	  this.port = port;
  }
  
  @Override
	public void onCreate(RESTResource resource) {
	    // TODO currently assumes resources are added after their parents
	    addRec(resource.getPath(), new CoAPResourceContainer(resource), server.getRoot());
	    
	    resource.addListener(this);
	}
  
  @Override
	public void onDelete(RESTResource resource) {
	    deleteRec(resource.getPath(), server.getRoot());
	}
  
	@Override
	public void setIndex(IndexResource index) {
		root = index;
		server = new CoapServer(port) {
			@Override
			protected Resource createRoot() {
				return new CoAPResourceContainer(root);
			}
		};
		index.addListener(this);
	}
  
  @Override
  public void start() {
	  
	  	// TODO server can only be started after setIndex() is called
    t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        server.start();
      }
    });
    t.start();
  }
  
  @Override
  public void stop() {
    t.stop();
  }
  
  @Override
  public void join() {
    try
    {
      t.join();
    }
    catch (InterruptedException e)
    {
      // TODO
      e.printStackTrace();
    }
  }
  
  protected void addRec(String path, Resource resource, Resource parent) {
    for (Resource r : parent.getChildren()) {
      if (path.contains(path(r))) {
        addRec(path, resource, r);
        return;
      }
    }
    parent.add(resource);
  }
  
  protected void deleteRec(String path, Resource parent) {
    if (path(parent).equals(path)) {
      parent.getParent().remove(parent);
      return;
    }
    for (Resource r : parent.getChildren()) {
      if (path.contains(path(r))) {
        deleteRec(path, r);
        return;
      }
    }
  }
  
  protected String path(Resource r) {
    return r.getPath() + r.getName();
  }
  
}