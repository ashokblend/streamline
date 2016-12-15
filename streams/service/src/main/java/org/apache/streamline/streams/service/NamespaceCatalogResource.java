package org.apache.streamline.streams.service;

import com.codahale.metrics.annotation.Timed;
import org.apache.commons.lang.BooleanUtils;
import org.apache.streamline.common.QueryParam;
import org.apache.streamline.common.util.WSUtils;
import org.apache.streamline.storage.exception.AlreadyExistsException;
import org.apache.streamline.streams.catalog.Namespace;
import org.apache.streamline.streams.catalog.NamespaceServiceClusterMapping;
import org.apache.streamline.streams.catalog.Topology;
import org.apache.streamline.streams.catalog.service.EnvironmentService;
import org.apache.streamline.streams.catalog.service.StreamCatalogService;
import org.apache.streamline.common.exception.service.exception.request.BadRequestException;
import org.apache.streamline.common.exception.service.exception.request.EntityNotFoundException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;

@Path("/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class NamespaceCatalogResource {
  private final StreamCatalogService catalogService;
  private final EnvironmentService environmentService;

  public NamespaceCatalogResource(StreamCatalogService catalogService, EnvironmentService environmentService) {
    this.catalogService = catalogService;
    this.environmentService = environmentService;
  }

  @GET
  @Path("/namespaces")
  @Timed
  public Response listNamespaces(@Context UriInfo uriInfo) {
    List<QueryParam> queryParams = new ArrayList<>();
    MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
    Collection<Namespace> namespaces;
    Boolean detail = false;

    if (params.isEmpty()) {
      namespaces = environmentService.listNamespaces();
    } else {
      MultivaluedMap<String, String> copiedParams = new MultivaluedHashMap<>();
      copiedParams.putAll(params);
      List<String> detailOption = copiedParams.remove("detail");
      if (detailOption != null && !detailOption.isEmpty()) {
        detail = BooleanUtils.toBooleanObject(detailOption.get(0));
      }

      queryParams = WSUtils.buildQueryParameters(copiedParams);
      namespaces = environmentService.listNamespaces(queryParams);
    }
    if (namespaces != null) {
      return buildNamespacesGetResponse(namespaces, detail);
    }

    throw EntityNotFoundException.byFilter(queryParams.toString());
  }

  @GET
  @Path("/namespaces/{id}")
  @Timed
  public Response getNamespaceById(@PathParam("id") Long namespaceId,
                                   @javax.ws.rs.QueryParam("detail") Boolean detail) {
    Namespace result = environmentService.getNamespace(namespaceId);
    if (result != null) {
      return buildNamespaceGetResponse(result, detail);
    }

    throw EntityNotFoundException.byId(namespaceId.toString());
  }

  @GET
  @Path("/namespaces/name/{namespaceName}")
  @Timed
  public Response getNamespaceByName(@PathParam("namespaceName") String namespaceName,
                                     @javax.ws.rs.QueryParam("detail") Boolean detail) {
    Namespace result = environmentService.getNamespaceByName(namespaceName);
    if (result != null) {
      return buildNamespaceGetResponse(result, detail);
    }

    throw EntityNotFoundException.byName(namespaceName);
  }

  @Timed
  @POST
  @Path("/namespaces")
  public Response addNamespace(Namespace namespace) {
    try {
      String namespaceName = namespace.getName();
      Namespace result = environmentService.getNamespaceByName(namespaceName);
      if (result != null) {
        throw new AlreadyExistsException("Namespace entity already exists with name " + namespaceName);
      }

      Namespace created = environmentService.addNamespace(namespace);
      return WSUtils.respondEntity(created, CREATED);
    } catch (ProcessingException ex) {
      throw BadRequestException.of();
    }
  }

  @DELETE
  @Path("/namespaces/{id}")
  @Timed
  public Response removeNamespace(@PathParam("id") Long namespaceId) {
    assertNoTopologyRefersNamespace(namespaceId);

    Namespace removed = environmentService.removeNamespace(namespaceId);
    if (removed != null) {
      return WSUtils.respondEntity(removed, OK);
    }

    throw EntityNotFoundException.byId(namespaceId.toString());
  }

  private void assertNoTopologyRefersNamespace(Long namespaceId) {
    Collection<Topology> topologies = catalogService.listTopologies();
    boolean anyTopologyUseNamespace = topologies.stream()
            .anyMatch(t -> Objects.equals(t.getNamespaceId(), namespaceId));

    if (anyTopologyUseNamespace) {
      throw BadRequestException.message("Topology refers the namespace trying to remove - namespace id: " + namespaceId);
    }
  }

  @PUT
  @Path("/namespaces/{id}")
  @Timed
  public Response addOrUpdateNamespace(@PathParam("id") Long namespaceId,
      Namespace namespace) {
    try {
      Namespace newNamespace = environmentService.addOrUpdateNamespace(namespaceId, namespace);
      return WSUtils.respondEntity(newNamespace, OK);
    } catch (ProcessingException ex) {
      throw BadRequestException.of();
    }
  }

  @GET
  @Path("/namespaces/{id}/mapping")
  @Timed
  public Response listServiceToClusterMappingInNamespace(@PathParam("id") Long namespaceId) {
    Namespace namespace = environmentService.getNamespace(namespaceId);
    if (namespace == null) {
      throw EntityNotFoundException.byId(namespaceId.toString());
    }

    Collection<NamespaceServiceClusterMapping> existingMappings = environmentService.listServiceClusterMapping(namespaceId);
    if (existingMappings != null) {
      return WSUtils.respondEntities(existingMappings, OK);
    }

    return WSUtils.respondEntities(Collections.emptyList(), OK);
  }

  @GET
  @Path("/namespaces/{id}/mapping/{serviceName}")
  @Timed
  public Response findServicesToClusterMappingInNamespace(@PathParam("id") Long namespaceId,
                                                          @PathParam("serviceName") String serviceName) {
    Namespace namespace = environmentService.getNamespace(namespaceId);
    if (namespace == null) {
      throw EntityNotFoundException.byId("Namespace: " + namespaceId.toString());
    }

    Collection<NamespaceServiceClusterMapping> mappings = environmentService.listServiceClusterMapping(namespaceId, serviceName);
    if (mappings != null) {
      return WSUtils.respondEntities(mappings, OK);
    } else {
      return WSUtils.respondEntities(Collections.emptyList(), OK);
    }
  }

  @POST
  @Path("/namespaces/{id}/mapping/bulk")
  @Timed
  public Response setServicesToClusterInNamespace(@PathParam("id") Long namespaceId,
                                                  List<NamespaceServiceClusterMapping> mappings) {
    Namespace namespace = environmentService.getNamespace(namespaceId);
    if (namespace == null) {
      throw EntityNotFoundException.byId(namespaceId.toString());
    }

    // remove any existing mapping for (namespace, service name) pairs
    Collection<NamespaceServiceClusterMapping> existingMappings = environmentService.listServiceClusterMapping(namespaceId);
    if (existingMappings != null) {
      existingMappings.forEach(m -> environmentService.removeServiceClusterMapping(m.getNamespaceId(), m.getServiceName(),
              m.getClusterId()));
    }

    List<NamespaceServiceClusterMapping> newMappings = mappings.stream()
            .map(environmentService::addOrUpdateServiceClusterMapping).collect(toList());

    return WSUtils.respondEntities(newMappings, CREATED);
  }

  @POST
  @Path("/namespaces/{id}/mapping")
  @Timed
  public Response mapServiceToClusterInNamespace(@PathParam("id") Long namespaceId,
            NamespaceServiceClusterMapping mapping) {
    Namespace namespace = environmentService.getNamespace(namespaceId);
    if (namespace == null) {
      throw EntityNotFoundException.byId(namespaceId.toString());
    }

    NamespaceServiceClusterMapping newMapping = environmentService.addOrUpdateServiceClusterMapping(mapping);
    return WSUtils.respondEntity(newMapping, CREATED);
  }

  @DELETE
  @Path("/namespaces/{id}/mapping/{serviceName}/cluster/{clusterId}")
  @Timed
  public Response unmapServiceToClusterInNamespace(@PathParam("id") Long namespaceId,
      @PathParam("serviceName") String serviceName, @PathParam("clusterId") Long clusterId) {
    NamespaceServiceClusterMapping mapping = environmentService.removeServiceClusterMapping(namespaceId, serviceName, clusterId);
    if (mapping != null) {
      return WSUtils.respondEntity(mapping, OK);
    }

    throw EntityNotFoundException.byId(buildMessageForCompositeId(namespaceId, serviceName, clusterId));
  }

  @DELETE
  @Path("/namespaces/{id}/mapping")
  @Timed
  public Response unmapAllServicesToClusterInNamespace(@PathParam("id") Long namespaceId) {
    List<NamespaceServiceClusterMapping> mappings = environmentService.listServiceClusterMapping(namespaceId).stream()
            .map((x) -> environmentService.removeServiceClusterMapping(x.getNamespaceId(), x.getServiceName(),
                    x.getClusterId()))
            .collect(toList());
    return WSUtils.respondEntities(mappings, OK);
  }

  private Response buildNamespacesGetResponse(Collection<Namespace> namespaces, Boolean detail) {
    if (BooleanUtils.isTrue(detail)) {
      List<NamespaceWithMapping> namespacesWithMapping = namespaces.stream()
              .map(this::buildNamespaceWithMapping)
              .collect(toList());

      return WSUtils.respondEntities(namespacesWithMapping, OK);
    } else {
      return WSUtils.respondEntities(namespaces, OK);
    }
  }

  private Response buildNamespaceGetResponse(Namespace namespace, Boolean detail) {
    if (BooleanUtils.isTrue(detail)) {
      NamespaceWithMapping namespaceWithMapping = buildNamespaceWithMapping(namespace);
      return WSUtils.respondEntity(namespaceWithMapping, OK);
    } else {
      return WSUtils.respondEntity(namespace, OK);
    }
  }

  private NamespaceWithMapping buildNamespaceWithMapping(Namespace namespace) {
    NamespaceWithMapping nm = new NamespaceWithMapping(namespace);
    nm.setServiceClusterMappings(environmentService.listServiceClusterMapping(namespace.getId()));
    return nm;
  }

  private static class NamespaceWithMapping {
    private Namespace namespace;
    private Collection<NamespaceServiceClusterMapping> mappings = new ArrayList<>();

    public NamespaceWithMapping(Namespace namespace) {
      this.namespace = namespace;
    }

    public Namespace getNamespace() {
      return namespace;
    }

    public Collection<NamespaceServiceClusterMapping> getMappings() {
      return mappings;
    }

    public void setServiceClusterMappings(Collection<NamespaceServiceClusterMapping> mappings) {
      this.mappings = mappings;
    }

    public void addServiceClusterMapping(NamespaceServiceClusterMapping mapping) {
      mappings.add(mapping);
    }
  }

  private String buildMessageForCompositeId(Long namespaceId, String serviceName) {
    return "Namespace: " + namespaceId.toString() + " / serviceName: " + serviceName;
  }

  private String buildMessageForCompositeId(Long namespaceId, String serviceName, Long clusterId) {
    return "Namespace: " + namespaceId.toString() + " / serviceName: " + serviceName + " / clusterId: " + clusterId;
  }

}
