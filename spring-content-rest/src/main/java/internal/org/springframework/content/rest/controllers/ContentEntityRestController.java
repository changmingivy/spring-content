package internal.org.springframework.content.rest.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.annotations.MimeType;
import org.springframework.content.commons.renditions.Renderable;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.storeservice.ContentStoreInfo;
import org.springframework.content.commons.storeservice.ContentStoreService;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.repository.support.Repositories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import internal.org.springframework.content.rest.annotations.ContentRestController;
import internal.org.springframework.content.rest.mappings.ContentHandlerMapping.StoreType;
import internal.org.springframework.content.rest.mappings.StoreByteRangeHttpRequestHandler;
import internal.org.springframework.content.rest.utils.ContentStoreUtils;

@ContentRestController
public class ContentEntityRestController extends AbstractContentPropertyController {

	private static final String BASE_MAPPING = "/{repository}/{id}";

	private Repositories repositories;
	private ContentStoreService storeService;
	private StoreByteRangeHttpRequestHandler handler;

	@Autowired 
	public ContentEntityRestController(ApplicationContext context, ContentStoreService storeService, StoreByteRangeHttpRequestHandler handler) {
		super();
		try {
			this.repositories = context.getBean(Repositories.class);
		} catch (BeansException be) {
			this.repositories = new Repositories(context);
		}
		this.storeService = storeService;
		this.handler = handler;
	}

	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.GET, headers={"accept!=application/hal+json", "range"})
	public void getContent(HttpServletRequest request, 
						   HttpServletResponse response,
						   @PathVariable String repository, 
						   @PathVariable String id) 
			throws HttpRequestMethodNotSupportedException {
		
		Object domainObj = findOne(repositories, repository, id);
		
		ContentStoreInfo info = ContentStoreUtils.findStore(storeService, repository);

		if (info == null) {
			throw new IllegalArgumentException("Entity not a content repository");
		}

		Serializable cid = (Serializable) BeanUtils.getFieldWithAnnotation(domainObj, ContentId.class);
		
		Resource r = ((Store)info.getImpementation()).getResource(cid);
		if (r == null) {
			throw new ResourceNotFoundException();
		}

		request.setAttribute("SPRING_CONTENT_RESOURCE", r);

		if (BeanUtils.hasFieldWithAnnotation(domainObj, MimeType.class)) {
			request.setAttribute("SPRING_CONTENT_CONTENTTYPE", BeanUtils.getFieldWithAnnotation(domainObj, MimeType.class).toString());
		}
		
		try {
			handler.handleRequest(request, response);
		} catch (ServletException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return;
	}
	
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.GET, headers="accept!=application/hal+json")
	public ResponseEntity<InputStreamResource> getContent(@PathVariable String repository, 
														  @PathVariable String id, 
														  @RequestHeader(value="Accept", required=false) String mimeType) 
			throws HttpRequestMethodNotSupportedException {
		
		Object domainObj = findOne(repositories, repository, id);
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, domainObj.getClass());
		if (info == null) {
			throw new IllegalArgumentException("Entity not a content repository");
		}

		InputStream content = info.getImpementation().getContent(domainObj);
		if (content == null) {
			throw new ResourceNotFoundException();
		}
		
		Object contentTypeObj = BeanUtils.getFieldWithAnnotation(domainObj, MimeType.class);
		String contentType = contentTypeObj != null ? contentTypeObj.toString() : null;
		if (mimeType == null || mimeType.contains("*/*") || mimeType.equals(contentType)) {
			final HttpHeaders headers = new HttpHeaders();
			if (BeanUtils.hasFieldWithAnnotation(domainObj, MimeType.class)) {
				headers.add("Content-Type", BeanUtils.getFieldWithAnnotation(domainObj, MimeType.class).toString());
			}
			if (BeanUtils.hasFieldWithAnnotation(domainObj, ContentLength.class))
				headers.add("Content-Length", BeanUtils.getFieldWithAnnotation(domainObj, ContentLength.class).toString());
			
			InputStreamResource inputStreamResource = new InputStreamResource(info.getImpementation().getContent(domainObj));
			return new ResponseEntity<InputStreamResource>(inputStreamResource, headers, HttpStatus.OK);
		} else {
			final HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", mimeType);
//			if (BeanUtils.hasFieldWithAnnotation(contentPropertyValue, ContentLength.class))
//				headers.add("Content-Length", BeanUtils.getFieldWithAnnotation(contentPropertyValue, ContentLength.class).toString());
			
			ContentStore<Object,Serializable> impl = info.getImpementation();
			
			if (impl instanceof Renderable) {
				InputStream is = ((Renderable)impl).getRendition(domainObj, mimeType);
				if (is != null) {
					InputStreamResource inputStreamResource = new InputStreamResource(is);
					return new ResponseEntity<InputStreamResource>(inputStreamResource, headers, HttpStatus.OK);
				} else {
					return new ResponseEntity<InputStreamResource>(null, headers, HttpStatus.NOT_ACCEPTABLE);
				}
			}
		}
		return null;
	}
	
	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.PUT, headers={"content-type!=multipart/form-data", "accept!=application/hal+json"})
	@ResponseBody
	public void putContent(HttpServletRequest request,
						   HttpServletResponse response,
        				   @PathVariable String repository, 
						   @PathVariable String id) 
			throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {

		Object domainObj = findOne(repositories, repository, id);
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, domainObj.getClass());
		if (info == null) {
			throw new IllegalArgumentException("Entity not a content repository");
		}

		boolean isNew = true;
		if (BeanUtils.hasFieldWithAnnotation(domainObj, ContentId.class)) {
			isNew = (BeanUtils.getFieldWithAnnotation(domainObj, ContentId.class) == null);
		}
		
		info.getImpementation().setContent(domainObj, request.getInputStream());
		
		if (BeanUtils.hasFieldWithAnnotation(domainObj, MimeType.class)) {
			BeanUtils.setFieldWithAnnotation(domainObj, MimeType.class, request.getHeader("Content-Type"));
		}
		
		save(repositories, repository, domainObj);
		
		if (isNew) {
			response.setStatus(HttpStatus.CREATED.value());
		} else {
			response.setStatus(HttpStatus.OK.value());
		}
	}
	
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.PUT, headers = "content-type=multipart/form-data")
	@ResponseBody
	public void putMultipartContent(HttpServletResponse response,
									@PathVariable String repository, 
									@PathVariable String id, 
									@RequestParam("file") MultipartFile multiPart)
											 throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {
		handleMultipart(response, repository, id, multiPart);
	}
	
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.POST, headers = "content-type=multipart/form-data")
	@ResponseBody
	public void postMultipartContent(HttpServletResponse response,
									 @PathVariable String repository, 
									 @PathVariable String id, 
									 @RequestParam("file") MultipartFile multiPart)
											 throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {
		handleMultipart(response, repository, id, multiPart);
	}
	
	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.DELETE, headers="accept!=application/hal+json")
	public void deleteContent(HttpServletResponse response,
							  @PathVariable String repository, 
							  @PathVariable String id) 
			throws HttpRequestMethodNotSupportedException {

		Object domainObj = findOne(repositories, repository, id);
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, domainObj.getClass());
		if (info == null) {
			throw new IllegalArgumentException("Entity not a content repository");
		}

		if (info.getImpementation().getContent(domainObj) == null) {
			throw new ResourceNotFoundException();
		}
		
		info.getImpementation().unsetContent(domainObj);
		
		if (BeanUtils.hasFieldWithAnnotation(domainObj, MimeType.class)) {
			BeanUtils.setFieldWithAnnotation(domainObj, MimeType.class, null);
		}
		
		save(repositories, repository, domainObj);
		
		response.setStatus(HttpStatus.NO_CONTENT.value());
	}

	protected void handleMultipart(HttpServletResponse response, String repository, String id, MultipartFile multiPart)
			throws HttpRequestMethodNotSupportedException, IOException {

		Object domainObj = findOne(repositories, repository, id);
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, domainObj.getClass());
		if (info == null) {
			throw new IllegalArgumentException("Entity not a content repository");
		}

		info.getImpementation().setContent(domainObj, multiPart.getInputStream());
		
		if (BeanUtils.hasFieldWithAnnotation(domainObj, MimeType.class)) {
			BeanUtils.setFieldWithAnnotation(domainObj, MimeType.class, multiPart.getContentType());
		}
		
		boolean isNew = true;
		if (BeanUtils.hasFieldWithAnnotation(domainObj, ContentId.class)) {
			isNew = (BeanUtils.getFieldWithAnnotation(domainObj, ContentId.class) == null);
		}
		
		save(repositories, repository, domainObj);
		
		if (isNew) {
			response.setStatus(HttpStatus.CREATED.value());
		} else {
			response.setStatus(HttpStatus.OK.value());
		}
	}
}
