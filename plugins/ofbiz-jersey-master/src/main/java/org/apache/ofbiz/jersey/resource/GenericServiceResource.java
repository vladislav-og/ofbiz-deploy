package org.apache.ofbiz.jersey.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.ExtendedConverters;
import org.apache.ofbiz.jersey.annotation.Secured;
import org.apache.ofbiz.jersey.response.Error;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelParam;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Path("/generic/v1/services")
@Provider
@Secured
public class GenericServiceResource {

	public static final String MODULE = GenericServiceResource.class.getName();
	public static final ExtendedConverters.ExtendedJSONToGenericValue jsonToGenericConverter = new ExtendedConverters.ExtendedJSONToGenericValue();
	private static final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

	@Context
	private HttpServletRequest httpRequest;

	@Context
	private ServletContext servletContext;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getServiceNames() {
		Response.ResponseBuilder builder;
		LocalDispatcher dispatcher = (LocalDispatcher) servletContext.getAttribute("dispatcher");
		DispatchContext dpc = dispatcher.getDispatchContext();
		builder = Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(dpc.getAllServiceNames());
		return builder.build();
	}

	@GET
	@Path("/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getServiceDetails(@PathParam(value = "name") String serviceName) {
		Response.ResponseBuilder builder;
		LocalDispatcher dispatcher = (LocalDispatcher) servletContext.getAttribute("dispatcher");
		DispatchContext dpc = dispatcher.getDispatchContext();
		List<ModelParam> obj;
		try {
			obj = dpc.getModelService(serviceName).getModelParamList();
		} catch (GenericServiceException e) {
			Error error = new Error(400, "Bad Request", "Error getting service of given name.");
			builder = Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE).entity(error);
			e.printStackTrace();
			return builder.build();
		}
		JSON json;
		try {
			json = JSON.from(mapper.writeValueAsString(obj));
		} catch (JsonProcessingException e) {
			Error error = new Error(500, "Internal Server Error", "Error converting structure to JSON.");
			builder = Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE).entity(error);
			e.printStackTrace();
			return builder.build();
		}
		builder = Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(json.toString());
		return builder.build();
	}

	@POST
	@Path("/{name}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response callService(@PathParam(value = "name") String serviceName, String jsonBody) {
		Response.ResponseBuilder builder;
		LocalDispatcher dispatcher = (LocalDispatcher) servletContext.getAttribute("dispatcher");
		Delegator delegator = (Delegator) servletContext.getAttribute("delegator");

		JSON body = JSON.from(jsonBody);

		Map<String, Object> fieldMap;

		try {
			fieldMap = UtilGenerics.<Map<String, Object>>cast(body.toObject(Map.class));
		} catch (IOException e) {
			Error error = new Error(400, "Bad Request", "Error converting JSON to Map.");
			builder = Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON_TYPE).entity(error);
			e.printStackTrace();
			return builder.build();
		}
		// TODO: recursive converting to support multi level objects
		for (String key : fieldMap.keySet()) {
			Object obj = fieldMap.get(key);
			try {
				Map<String, Object> test = UtilGenerics.<Map<String, Object>>cast(JSON.from(obj).toObject(Map.class));
				if (test.containsKey("_ENTITY_NAME_")) {
					fieldMap.put(key, jsonToGenericConverter.convert(delegator.getDelegatorName(), JSON.from(obj)));
				} else {
					fieldMap.put(key, test);
				}
			} catch (IOException | ConversionException ignored) {
				// Ignore as it just means the value isn't a separate object
			}
		}
		try {
			Map<String, ?> entity = dispatcher.runSync(serviceName, fieldMap);
			builder = Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(entity);
		} catch (GenericServiceException e) {
			e.printStackTrace();
			Error error = new Error(500, "Bad Request", e.getMessage());
			builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON).entity(error);
		}
		return builder.build();
	}

}
