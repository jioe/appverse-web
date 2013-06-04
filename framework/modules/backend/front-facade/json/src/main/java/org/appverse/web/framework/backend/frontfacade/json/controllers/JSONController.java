/*
 Copyright (c) 2012 GFT Appverse, S.L., Sociedad Unipersonal.

 This Source Code Form is subject to the terms of the Appverse Public License 
 Version 2.0 (“APL v2.0”). If a copy of the APL was not distributed with this 
 file, You can obtain one at http://www.appverse.mobi/licenses/apl_v2.0.pdf. [^]

 Redistribution and use in source and binary forms, with or without modification, 
 are permitted provided that the conditions of the AppVerse Public License v2.0 
 are met.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. EXCEPT IN CASE OF WILLFUL MISCONDUCT OR GROSS NEGLIGENCE, IN NO EVENT
 SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT(INCLUDING NEGLIGENCE OR OTHERWISE) 
 ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 POSSIBILITY OF SUCH DAMAGE.
 */
package org.appverse.web.framework.backend.frontfacade.json.controllers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.appverse.web.framework.backend.frontfacade.json.controllers.exceptions.BadRequestException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Controller;

import com.sun.jersey.core.spi.factory.ResponseBuilderImpl;

@Controller
@Path("jsonservices")
public class JSONController {
	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	CustomMappingJacksonHttpMessageConverter customMappingJacksonHttpMessageConverter;

	private void addDefaultResponseHeaders(HttpServletResponse response) {
		// Add headers to prevent Cross-site ajax calls issues
		response.addHeader("Content-Type", "application/json; charset=UTF-8");
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Headers",
				"Content-Type,X-Requested-With");
	}

	@PostConstruct
	public void bindMessageConverters() {
		ObjectMapper mapper = new ObjectMapper();
		// mapper.setDateFormat(new
		// SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS"));
		// SerializationConfig sc = mapper.getSerializationConfig();
		customMappingJacksonHttpMessageConverter.setObjectMapper(mapper);
	}

	private String createXSRFToken(final HttpServletRequest request)
			throws IOException {
		HttpSession session = request.getSession();
		String xsrfSessionToken = (String) session
				.getAttribute("X-XSRF-Cookie");
		if (xsrfSessionToken == null) {
			Random r = new Random(System.currentTimeMillis());
			long value = System.currentTimeMillis() + r.nextLong();
			char ids[] = session.getId().toCharArray();
			for (int i = 0; i < ids.length; i++) {
				value += ids[i] * (i + 1);
			}
			xsrfSessionToken = Long.toString(value);
			session.setAttribute("X-XSRF-Cookie", xsrfSessionToken);
		}
		return xsrfSessionToken;
	}

	/**
	 * 
	 * @param request
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private void checkXSRFToken(final HttpServletRequest request)
			throws Exception {
		/**
		 * Currently this method is not used. Needs to be analyzed how this can
		 * be implemented in a "generic" way.
		 */
		String requestValue = request.getHeader("X-XSRF-Cookie");
		String sessionValue = (String) request.getSession().getAttribute(
				"X-XSRF-Cookie");
		if (sessionValue != null && !sessionValue.equals(requestValue)) {
			// throw new PreAuthenticatedCredentialsNotFoundException(
			// "XSRF attribute not found in session.");
			throw new Exception("XSRF attribute not found in session.");
		}
	}

	// @POST
	// @Consumes("application/json")
	// @Produces("application/json")
	// @Path("*.json")
	// public String handleRequest1(@Context HttpServletRequest request,
	// @Context HttpServletResponse response, @FormParam("payload") String
	// payload)
	// throws Exception {
	// System.out.println("handle request 1");
	// return "";
	// }

	/**
	 * Method to handle all requests to the Appverse Services Presentation Layer.
	 * It only accepts POST requests, with the parameter set on the payload.
	 * The URL must contain the servicename (spring name of the Presentation Service) and also the method name.
	 * The URL musb be something like: {protocol}://{host}:{port}/{appcontext}/{servicename}/{methodname}
	 * 
	 * @param requestServiceName The "spring" name of the Service.
	 * @param requestMethodName The method name
	 * @param response The HttpServletResponse, injected by Jersey.
	 * @param payload The payload must contain the parameter as json.
	 * @return
	 * @throws Exception In case of any Bad Request or an uncontrolled exception raised by the Service.
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{servicename}/{methodname}")
	public String handleRequest(
			@PathParam("servicename") String requestServiceName,
			@PathParam("methodname") String requestMethodName,
//			@Context HttpServletRequest request,
			@Context HttpServletResponse response,
			String payload) throws Exception {
		// String path = request.getServletPath();
		System.out.println("Request Received - " + requestServiceName+"."+requestMethodName);
		
		Object presentationService = applicationContext.getBean(requestServiceName);
		if (presentationService == null) {
			throw new BadRequestException(
					"Requested ServiceFacade don't exists " + requestServiceName);
		}
		// if (!(presentationService instanceof AuthenticationServiceFacade)) {
		// checkXSRFToken(request);
		Method[] methods = presentationService.getClass().getMethods();
		Method method = null;
		for (Method methodItem : methods) {
			if (methodItem.getName().equals(requestMethodName)) {
				method = methodItem;
				break;
			}
		}
		if (method == null) {
			throw new BadRequestException("Requested Method don't exists "
					+ requestMethodName + " for serviceFacade " + requestServiceName);
//			throw new IllegalArgumentException("Requested Method don't exists "
//					+ requestMethodName + " for serviceFacade " + requestServiceName);
		}
		Class<?>[] parameterTypes = method.getParameterTypes();
		Class<?> parameterType = null;
		if (parameterTypes.length > 1) {
			throw new BadRequestException("Requested Method" + requestMethodName
					+ " for serviceFacade " + requestServiceName
					+ " only accepts 0 or 1 parameter");
		}
		Object parameter = null;
		if (parameterTypes.length > 0) {
			parameterType = parameterTypes[0];
			try {
				parameter = customMappingJacksonHttpMessageConverter.readInternal(
						parameterType, payload);
			}catch(Throwable th) {
				throw new BadRequestException("Parameter of type " + parameterType.getCanonicalName()
						+ " can't be parsed from [" + payload
						+ "]");
			}
		} 
		try {
			Object result = null;
			if( parameter != null ) {
				result = method.invoke(presentationService, parameter);
			} else {
				result = method.invoke(presentationService);
			}
//			return Response.ok(result, MediaType.APPLICATION_JSON).build();
			ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(
					response);
			customMappingJacksonHttpMessageConverter.write(result,
					org.springframework.http.MediaType.APPLICATION_JSON, outputMessage);
			addDefaultResponseHeaders(response);
		} catch (Throwable th) {
//			response.sendError(500, th.getMessage());
			th.printStackTrace();
			ResponseBuilderImpl builder = new ResponseBuilderImpl();
			builder.status(Response.Status.INTERNAL_SERVER_ERROR);
			builder.entity("Service Internal Error ["+th.getCause()!=null?th.getCause().getMessage():th.getMessage()+"]");
			Response resp = builder.build();
			throw new WebApplicationException(resp);
		}
		return "";

	}

}