package com.petito.jsontransformer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {

    @Value("${remote.server.protocol}")
    private String protocol;

    @Value("${remote.server.host}")
    private String host;

    @Value("${remote.server.port}")
    private String port;

    @Value("${remote.server.path}")
    private String path;

    public String getFullServerUrl() {
        
        return String.format("%s://%s:%s%s", protocol, host, port, path);
    }


    public String getProtocol() { 
    	return protocol; 
    }
    
    public String getHost() { 
    	return host; 
    }
    
    public String getPort() { 
    	return port; 
    }
    
    public String getPath() { 
    	return path; 
    }
}