package internal.org.springframework.content.rest.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.webmvc.RootResourceInformation;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jSpringRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;

import internal.org.springframework.content.rest.TestConfig;
import internal.org.springframework.content.rest.TestEntity;
import internal.org.springframework.content.rest.TestEntity2;
import internal.org.springframework.content.rest.TestEntity2Repository;
import internal.org.springframework.content.rest.TestEntityChild;
import internal.org.springframework.content.rest.TestEntityChildContentRepository;
import internal.org.springframework.content.rest.TestEntityContentRepository;
import internal.org.springframework.content.rest.TestEntityRepository;

@RunWith(Ginkgo4jSpringRunner.class)
//@Ginkgo4jConfiguration(threads=1)
@WebAppConfiguration
@ContextConfiguration(classes = {TestConfig.class, DelegatingWebMvcConfiguration.class, RepositoryRestMvcConfiguration.class, RestConfiguration.class})
//@WebIntegrationTest
@Transactional
public class ContentPropertyRestControllerIntegrationTest {
	
	@Autowired Repositories repositories;
	@Autowired RepositoryInvokerFactory invokerFactory;
	@Autowired ResourceMappings mappings;
	
	@Autowired TestEntityRepository repository;
	@Autowired TestEntityContentRepository contentRepository;
	@Autowired TestEntity2Repository repository2;
	@Autowired TestEntityChildContentRepository contentRepository2;
	
	@Autowired ContentPropertyCollectionRestController collectionCtrlr;
	@Autowired ContentPropertyRestController propCtrlr;
	
	@Autowired private WebApplicationContext context;

	private MockMvc mvc;

	private TestEntity testEntity;
	
	private TestEntity2 testEntity2;
	private TestEntityChild child1;
	private TestEntityChild child2;

	{
		Describe("ContentPropertyRestController", () -> {
			BeforeEach(() -> {
				assertThat(collectionCtrlr, is(not(nullValue())));
				assertThat(propCtrlr, is(not(nullValue())));
				
				mvc = MockMvcBuilders
						.webAppContextSetup(context)
						.build();
			});
			Context("given an Entity with a simple content property", () -> {
				BeforeEach(() -> {
					testEntity2 = repository2.save(new TestEntity2());
					repository2.save(testEntity2);
				});
				Context("given that is has no content", () -> {
					Context("a GET to /{repository}/{id}/{contentProperty}", () -> {
						It("should return 404", () -> {
							mvc.perform(get("/files/" + testEntity2.id.toString() + "/child"))
									.andExpect(status().isNotFound());
						});
					});
					Context("a PUT to /{repository}/{id}/{contentProperty}", () -> {
						It("should create the content", () -> {
							mvc.perform(put("/files/" + testEntity2.id.toString() + "/child")
									.content("Hello New Spring Content World!")
									.contentType("text/plain"))
									.andExpect(status().is2xxSuccessful());
							
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.child.contentId, is(not(nullValue())));
							assertThat(fetched.child.contentLen, is(31L));
							assertThat(fetched.child.mimeType, is("text/plain"));
							assertThat(IOUtils.toString(contentRepository2.getContent(fetched.child)), is("Hello New Spring Content World!"));
						});
					});
				});
				Context("given that it has content", () -> {
					BeforeEach(() -> {
						testEntity2.child = new TestEntityChild();
						testEntity2.child.mimeType = "text/plain";
						contentRepository2.setContent(testEntity2.child, new ByteArrayInputStream("Hello Spring Content World!".getBytes()));
						repository2.save(testEntity2);
					});
					Context("a GET to /{repository}/{id}/{contentProperty}", () -> {
						It("should return the content", () -> {
							MockHttpServletResponse response = mvc.perform(get("/files/" + testEntity2.id.toString() + "/child")
									.accept("text/plain"))
									.andExpect(status().isOk())
									.andReturn().getResponse();

							assertThat(response, is(not(nullValue())));
							assertThat(response.getContentAsString(), is("Hello Spring Content World!"));
						});
					});
					Context("a range GET to /{repository}/{id}/{contentProperty}", () -> {
						It("should return the content", () -> {
							MockHttpServletResponse response = mvc.perform(get("/files/" + testEntity2.id.toString() + "/child")
									.accept("text/plain")
									.header("range", "bytes=6-19"))
									.andExpect(status().isPartialContent())
									.andReturn().getResponse();
							assertThat(response, is(not(nullValue())));

							assertThat(response.getContentAsString(), is("Spring Content"));
						});
					});
					Context("a GET to /{repository}/{id}/{contentProperty}/{contentId}", () -> {
						It("should return the content", () -> {
							MockHttpServletResponse response = mvc.perform(get("/files/" + testEntity2.id.toString() + "/child/" + testEntity2.child.contentId)
									.accept("text/plain"))
									.andExpect(status().isOk())
									.andReturn().getResponse();
							assertThat(response, is(not(nullValue())));

							assertThat(response.getContentAsString(), is("Hello Spring Content World!"));
						});
					});
					Context("a range GET to /{repository}/{id}/{contentProperty}/{contentId}", () -> {
						It("should return the content", () -> {
							MockHttpServletResponse response = mvc.perform(get("/files/" + testEntity2.id.toString() + "/child/" + testEntity2.child.contentId)
									.accept("text/plain")
									.header("range", "bytes=6-19"))
									.andExpect(status().isPartialContent())
									.andReturn().getResponse();
							assertThat(response, is(not(nullValue())));

							assertThat(response.getContentAsString(), is("Spring Content"));
						});
					});
					Context("a PUT to /{repository}/{id}/{contentProperty}", () -> {
						It("should create the content", () -> {
							mvc.perform(put("/files/" + testEntity2.id.toString() + "/child")
									.content("Hello New Spring Content World!")
									.contentType("text/plain"))
									.andExpect(status().is2xxSuccessful());
							
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.child.contentId, is(not(nullValue())));
							assertThat(fetched.child.contentLen, is(31L));
							assertThat(fetched.child.mimeType, is("text/plain"));
							assertThat(IOUtils.toString(contentRepository2.getContent(fetched.child)), is("Hello New Spring Content World!"));
						});
					});
					Context("a PUT to /{repository}/{id}/{contentProperty}/{contentId}", () -> {
						It("should overwrite the content", () -> {
							mvc.perform(put("/files/" + testEntity2.id.toString() + "/child/" + testEntity2.child.contentId)
									.content("Hello Modified Spring Content World!")
									.contentType("text/plain"))
									.andExpect(status().isOk());

							assertThat(IOUtils.toString(contentRepository2.getContent(testEntity2.child)), is("Hello Modified Spring Content World!"));
						});
					});
					Context("a DELETE to /{repository}/{id}/{contentProperty}", () -> {
						It("should delete the content", () -> {
							mvc.perform(delete("/files/" + testEntity2.id.toString() + "/child"))
									.andExpect(status().isNoContent());
							
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.child.contentId, is(nullValue()));
							assertThat(fetched.child.contentLen, is(0L));
							assertThat(fetched.child.mimeType, is(nullValue()));
						});
					});
					Context("a DELETE to /{repository}/{id}/{contentProperty}/{contentId}", () -> {
						It("should delete the content", () -> {
							mvc.perform(delete("/files/" + testEntity2.id.toString() + "/child/" + testEntity2.child.contentId))
									.andExpect(status().isNoContent());
							
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.child.contentId, is(nullValue()));
						});
					});
				});
			});
			
			Context("given an Entity with a collection content property", () -> {
				BeforeEach(() -> {
					testEntity2 = repository2.save(new TestEntity2());
				});
				Context("given that is has no content", () -> {
					Context("a GET to /{repository}/{id}/{contentProperty}", () -> {
						It("should return 406 MethodNotAllowed", () -> {
							mvc.perform(get("/files/" + testEntity2.id.toString() + "/children/"))
								.andExpect(status().isMethodNotAllowed());
						});
					});
					Context("a PUT to /{repository}/{id}/{contentProperty}", () -> {
						It("should append the content to the entity's content property collection", () -> {
							mvc.perform(put("/files/" + testEntity2.id.toString() + "/children/")
								.content("Hello New Spring Content World!")
								.contentType("text/plain"))
								.andExpect(status().is2xxSuccessful());
					
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.children.size(), is(1));
							assertThat(fetched.children.get(0).contentLen, is(31L));
							assertThat(fetched.children.get(0).mimeType, is("text/plain"));
							assertThat(IOUtils.toString(contentRepository2.getContent(fetched.getChildren().get(0))), is("Hello New Spring Content World!"));
						});
					});
					Context("a POST to /{repository}/{id}/{contentProperty}", () -> {
						It("should append the content to the entity's content property collection", () -> {
							mvc.perform(post("/files/" + testEntity2.id.toString() + "/children/")
								.content("Hello New Spring Content World!")
								.contentType("text/plain"))
								.andExpect(status().is2xxSuccessful());
					
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.children.size(), is(1));
							assertThat(fetched.children.get(0).contentLen, is(31L));
							assertThat(fetched.children.get(0).mimeType, is("text/plain"));
							assertThat(IOUtils.toString(contentRepository2.getContent(fetched.getChildren().get(0))), is("Hello New Spring Content World!"));
						});
					});
					Context("a DELETE to /{repository}/{id}/{contentProperty}", () -> {
						It("should return a 405 MethodNotAllowed", () -> {
							mvc.perform(delete("/files/" + testEntity2.id.toString() + "/children/"))
								.andExpect(status().isMethodNotAllowed());
						});
					});
				});
				Context("given that is has content", () -> {
					BeforeEach(() -> {
						testEntity2 = repository2.save(new TestEntity2());
						
						child1 = new TestEntityChild();
						child1.mimeType = "text/plain";
						contentRepository2.setContent(child1, new ByteArrayInputStream("Hello".getBytes()));
	
						child2 = new TestEntityChild();
						child2.mimeType = "text/plain";
						contentRepository2.setContent(child2, new ByteArrayInputStream("Spring Content World!".getBytes()));
	
						testEntity2.children = new ArrayList<TestEntityChild>();
						testEntity2.children.add(child1);
						testEntity2.children.add(child2);
						
						repository2.save(testEntity2);
					});
					Context("a GET to /{repository}/{id}/{contentCollectionProperty}/{contentId}", () -> {
						It("should return the content", () -> {
							MockHttpServletResponse response = mvc.perform(get("/files/" + testEntity2.id.toString() + "/children/" + child2.contentId)
									.accept("text/plain"))
									.andExpect(status().isOk())
									.andReturn().getResponse();
	
							assertThat(response, is(not(nullValue())));
							assertThat(response.getContentAsString(), is("Spring Content World!"));
						});
					});
					Context("a PUT to /{repository}/{id}/{contentCollectionProperty}/{contentId}", () -> {
						It("should set the content", () -> { 
							mvc.perform(put("/files/" + testEntity2.id.toString() + "/children/" + child2.contentId)
									.content("Modified Content World!")
									.contentType("text/plain"))
									.andExpect(status().isOk());
	
							assertThat(IOUtils.toString(contentRepository2.getContent(child2)), is("Modified Content World!"));
						});
					});
					Context("a POST to /{repository}/{id}/{contentCollectionProperty}/{contentId}", () -> {
						It("should set the content", () -> { 
							mvc.perform(post("/files/" + testEntity2.id.toString() + "/children/" + child2.contentId)
									.content("Modified Content World!")
									.contentType("text/plain"))
									.andExpect(status().isOk());
	
							assertThat(IOUtils.toString(contentRepository2.getContent(child2)), is("Modified Content World!"));
						});
					});
					Context("a DELETE to /{repository}/{id}/{contentCollectionProperty}/{contentId}", () -> {
						It("should delete the content", () -> { 
							mvc.perform(delete("/files/" + testEntity2.id.toString() + "/children/" + child2.contentId))
									.andExpect(status().isNoContent());
	
							assertThat(contentRepository2.getContent(child2), is(nullValue()));
	
							TestEntity2 fetched = repository2.findOne(testEntity2.id);
							assertThat(fetched.children.size(), is(2));
						});
					});
				});
			});
		});
	}

	@Test
	public void noop() {}
	
	protected RootResourceInformation getResourceInformation(Class<?> domainType) {

		Assert.notNull(domainType, "Domain type must not be null!");

		PersistentEntity<?, ?> entity = repositories.getPersistentEntity(domainType);

		return new RootResourceInformation(mappings.getMetadataFor(domainType), entity,
				invokerFactory.getInvokerFor(domainType));
	}
}
