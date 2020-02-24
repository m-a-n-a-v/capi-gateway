package at.rodrigo.api.gateway.cache;

import at.rodrigo.api.gateway.entity.Api;
import at.rodrigo.api.gateway.entity.Path;
import at.rodrigo.api.gateway.parser.SwaggerParser;
import at.rodrigo.api.gateway.routes.SimpleRestRouteRepublisher;
import at.rodrigo.api.gateway.routes.SwaggerRouteRepublisher;
import at.rodrigo.api.gateway.utils.CamelUtils;
import at.rodrigo.api.gateway.utils.GrafanaUtils;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class NewApiListener implements EntryAddedListener<String, Api>, EntryRemovedListener<String, Api> {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private CamelUtils camelUtils;

    @Autowired
    private GrafanaUtils grafanaUtils;

    @Autowired
    private SwaggerParser swaggerParser;

    @Autowired
    private ThrottlingManager throttlingManager;

    @Override
    public void entryAdded( EntryEvent<String, Api> event ) {
        log.info("One new API detected, deploying");
        try {
            Api api = event.getValue();
            if(api.getPaths() != null) {
                for(Path path : api.getPaths()) {
                    String routeID = camelUtils.normalizeRouteId(api, path);
                    if(camelContext.getRoute(routeID) != null) {
                        camelContext.getRouteController().stopRoute(routeID);
                        camelContext.removeRoute(routeID);
                    }
                }
            }
            if(api.getSwaggerEndpoint() == null) {
                camelContext.addRoutes(new SimpleRestRouteRepublisher(camelContext, camelUtils, grafanaUtils, throttlingManager, api));
            } else {
                List<Path> pathList = swaggerParser.parse(api.getSwaggerEndpoint());
                for(Path path : pathList) {
                    String routeID = camelUtils.normalizeRouteId(api, path);
                    if(camelContext.getRoute(routeID) != null) {
                        camelContext.getRouteController().stopRoute(routeID);
                        camelContext.removeRoute(routeID);
                    }
                }
                api.setPaths(pathList);
                camelContext.addRoutes(new SwaggerRouteRepublisher(camelContext, camelUtils, grafanaUtils, throttlingManager, api));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void entryRemoved( EntryEvent<String, Api> event ) {
        log.info("API deleted, undeploying");
        try {
            Api api = event.getValue();
            if(api.getPaths() != null) {
                for(Path path : api.getPaths()) {
                    String routeID = camelUtils.normalizeRouteId(api, path);
                    if(camelContext.getRoute(routeID) != null) {
                        camelContext.getRouteController().stopRoute(routeID);
                        camelContext.removeRoute(routeID);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}