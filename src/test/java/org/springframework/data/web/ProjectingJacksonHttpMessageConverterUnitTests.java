/*
 * Copyright 2025-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.web;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;

/**
 * Unit tests for {@link ProjectingJacksonHttpMessageConverter}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
class ProjectingJacksonHttpMessageConverterUnitTests {

	ProjectingJacksonHttpMessageConverter converter = new ProjectingJacksonHttpMessageConverter();
	MediaType ANYTHING_JSON = MediaType.parseMediaType("application/*+json");

	@Test
	void canReadJsonIntoAnnotatedInterface() {
		assertThat(converter.canRead(SampleInterface.class, ANYTHING_JSON)).isTrue();
	}

	@Test
	void readsProjectedPayloadWithOptionalGetter() throws Exception {

		var projection = (OptionalPayload) converter.read(ResolvableType.forClass(OptionalPayload.class),
				inputMessage("{\"firstname\":\"Dave\",\"nickname\":null,\"address\":{\"city\":\"Dresden\"}}"),
				null);

		assertThat(projection.getFirstname()).hasValue("Dave");
		assertThat(projection.getNickname()).isNotNull().isEmpty();
		assertThat(projection.getAddress()).isPresent()
				.get()
				.extracting(AddressProjection::getCity)
				.isEqualTo("Dresden");
	}

	@Test
	void cannotReadUnannotatedInterface() {
		assertThat(converter.canRead(UnannotatedInterface.class, ANYTHING_JSON)).isFalse();
	}

	@Test
	void cannotReadClass() {
		assertThat(converter.canRead(SampleClass.class, ANYTHING_JSON)).isFalse();
	}

	@Test
	void doesNotConsiderTypeVariableBoundTo() throws Throwable {

		var method = BaseController.class.getDeclaredMethod("createEntity", AbstractDto.class);

		assertThat(converter.canRead(ResolvableType.forMethodParameter(method, 0), ANYTHING_JSON)).isFalse();
	}

	@Test
	void genericTypeOnConcreteOne() throws Throwable {

		var method = ConcreteController.class.getMethod("createEntity", AbstractDto.class);

		assertThat(converter.canRead(ResolvableType.forMethodParameter(method, 0), ANYTHING_JSON)).isFalse();
	}

	private HttpInputMessage inputMessage(String json) {
		return new HttpInputMessage() {
			@Override
			public InputStream getBody() {
				return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
			}

			@Override
			public HttpHeaders getHeaders() {
				return new HttpHeaders();
			}
		};
	}

	@ProjectedPayload
	interface SampleInterface {}

	@ProjectedPayload
	interface OptionalPayload {

		Optional<String> getFirstname();

		@JsonPath("$.nickname")
		Optional<String> getNickname();

		Optional<AddressProjection> getAddress();
	}

	interface AddressProjection {

		String getCity();
	}

	interface UnannotatedInterface {}

	class SampleClass {}

	class AbstractDto {}

	abstract class BaseController<D extends AbstractDto> {
		public void createEntity(D dto) {}
	}

	class ConcreteController extends BaseController<AbstractDto> {}
}
