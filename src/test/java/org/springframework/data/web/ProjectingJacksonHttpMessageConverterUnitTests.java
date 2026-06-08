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

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Unit tests for {@link ProjectingJacksonHttpMessageConverter}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
class ProjectingJacksonHttpMessageConverterUnitTests {

	ProjectingJacksonHttpMessageConverter converter = new ProjectingJacksonHttpMessageConverter();
	MediaType ANYTHING_JSON = MediaType.parseMediaType("application/*+json");

	@Test // DATCMNS-885
	void canReadJsonIntoAnnotatedInterface() {
		assertThat(converter.canRead(SampleInterface.class, ANYTHING_JSON)).isTrue();
	}

	@Test // DATCMNS-885
	void cannotReadUnannotatedInterface() {
		assertThat(converter.canRead(UnannotatedInterface.class, ANYTHING_JSON)).isFalse();
	}

	@Test // DATCMNS-885
	void cannotReadClass() {
		assertThat(converter.canRead(SampleClass.class, ANYTHING_JSON)).isFalse();
	}

	@Test // DATACMNS-972
	void doesNotConsiderTypeVariableBoundTo() throws Throwable {

		var method = BaseController.class.getDeclaredMethod("createEntity", AbstractDto.class);

		assertThat(converter.canRead(ResolvableType.forMethodParameter(method, 0), ANYTHING_JSON)).isFalse();
	}

	@Test // DATACMNS-972
	void genericTypeOnConcreteOne() throws Throwable {

		var method = ConcreteController.class.getMethod("createEntity", AbstractDto.class);

		assertThat(converter.canRead(ResolvableType.forMethodParameter(method, 0), ANYTHING_JSON)).isFalse();
	}

	@ProjectedPayload
	interface SampleInterface {}

	interface UnannotatedInterface {}

	class SampleClass {}

	class AbstractDto {}

	abstract class BaseController<D extends AbstractDto> {
		public void createEntity(D dto) {}
	}

	class ConcreteController extends BaseController<AbstractDto> {}

	@Test
	void jackson3ConverterCanReadProjectedPayloadWithOptionalGetter() throws IOException {
		var json = "{\"firstname\" : \"Dave\", \"age\" : 42, \"active\" : true, \"nullField\" : null, "
				+ "\"address\" : { \"zipCode\" : \"01097\", \"city\" : \"Dresden\" }, "
				+ "\"addresses\" : [ { \"zipCode\" : \"01097\", \"city\" : \"Dresden\" } ] }";

		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		var inputMessage = new org.springframework.http.HttpInputMessage() {
			@Override
			public java.io.InputStream getBody() {
				return new ByteArrayInputStream(bytes);
			}
			@Override
			public org.springframework.http.HttpHeaders getHeaders() {
				return new org.springframework.http.HttpHeaders();
			}
		};
		var result = converter.read(ResolvableType.forClass(OptionalPayload.class), inputMessage, null);

		assertThat(result).isInstanceOf(OptionalPayload.class);
		var projection = (OptionalPayload) result;
		assertThat(projection.getFirstname()).hasValue("Dave");
		assertThat(projection.getAge()).hasValue(42);
		assertThat(projection.getActive()).hasValue(true);
		assertThat(projection.getNullField()).isEmpty();
		assertThat(projection.getMissingField()).isEmpty();
		assertThat(projection.getAddress()).isPresent();
		assertThat(projection.getAddress().get().getZipCode()).isEqualTo("01097");
		assertThat(projection.getAddresses()).isPresent();
		assertThat(projection.getAddresses().get()).hasSize(1);
		assertThat(projection.getAddressProjection()).isPresent();
		assertThat(projection.getAddressProjection().get().getCity()).isEqualTo("Dresden");
	}

	@Test
	void unannotatedInterfaceIsStillNotReadableByConverter() {
		assertThat(converter.canRead(UnannotatedInterface.class, ANYTHING_JSON)).isFalse();
	}

	@ProjectedPayload
	interface OptionalPayload {

		Optional<String> getFirstname();

		Optional<Integer> getAge();

		Optional<Boolean> getActive();

		Optional<String> getNullField();

		Optional<String> getMissingField();

		Optional<AddressDto> getAddress();

		Optional<List<AddressDto>> getAddresses();

		@JsonPath("$.address")
		Optional<AddressProjection> getAddressProjection();

		interface AddressProjection {
			String getCity();
		}
	}

	static class AddressDto {
		private String zipCode, city;

		public AddressDto() {}

		public String getZipCode() {
			return zipCode;
		}

		public void setZipCode(String zipCode) {
			this.zipCode = zipCode;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}
	}
}
