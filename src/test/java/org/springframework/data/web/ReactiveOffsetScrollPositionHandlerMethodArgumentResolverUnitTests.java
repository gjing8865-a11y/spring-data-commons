/*
 * Copyright 2023-present the original author or authors.
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

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.StringUtils;

/**
 * Unit tests for {@link ReactiveOffsetScrollPositionHandlerMethodArgumentResolver}.
 *
 * @author Yanming Zhou
 * @author Mark Paluch
 */
class ReactiveOffsetScrollPositionHandlerMethodArgumentResolverUnitTests {

	static final MethodParameter PARAMETER = getParameterOfMethod("supportedMethod");
	static final MethodParameter OPTIONAL_PARAMETER = getOptionalParameterOfMethod("supportedMethodWithOptional");

	final ReactiveOffsetScrollPositionHandlerMethodArgumentResolver sut = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();

	@Test // GH-2856
	void supportsOffsetScrollPositionParameter() {
		assertThat(sut.supportsParameter(PARAMETER)).isTrue();
	}

	@Test // GH-2856
	void supportsOptionalOffsetScrollPositionParameter() {
		assertThat(sut.supportsParameter(OPTIONAL_PARAMETER)).isTrue();
	}

	@Test // GH-2856
	void fallbackToNullOffset() {

		var parameter = TestUtils.getParameterOfMethod(Controller.class, "unsupportedMethod", String.class);
		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(request, parameter);

		assertThat(position).isNull();
	}

	@Test // GH-2856
	void discoversOffsetFromRequest() {

		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(getRequestWithOffset(reference, null), PARAMETER, reference);
	}

	@Test // GH-2856
	void returnsNullIfNotSpecified() {

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(request, PARAMETER);

		assertThat(position).isNull();
	}

	@Test // GH-2856
	void returnsOptionalParameterFromRequest() {

		var request = MockServerHttpRequest.get("/foo?offset=5").build();
		var position = resolveOffset(request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.of(ScrollPosition.offset(5)));
	}

	@Test // GH-2856
	void returnsEmptyOptionalIfNotSpecified() {

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffset(request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.empty());
	}

	@Test // GH-2856
	void discoversOffsetFromRequestWithMultipleParams() {

		var request = MockServerHttpRequest.get("/foo?offset=5&offset=6").build();

		assertThat(resolveOffset(request, PARAMETER)).isEqualTo(ScrollPosition.offset(5));
	}

	@Test // GH-2856
	void discoversQualifiedOffsetFromRequest() {

		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(getRequestWithOffset(reference, "hello"), parameter, reference);
	}

	@Test // GH-2856
	void returnsNullForEmptyOffsetParam() {

		var request = MockServerHttpRequest.get("/foo?offset=").build();

		assertThat(resolveOffset(request, PARAMETER)).isNull();
	}

	@Test // GH-2856
	void returnsNullForOffsetParamIsInvalidProperty() {

		var request = MockServerHttpRequest.get("/foo?offset=invalid_number").build();

		assertThat(resolveOffset(request, PARAMETER)).isNull();
	}

	@Test // GH-2856
	void emptyQualifierIsUsedInParameterLookup() {

		var parameter = getParameterOfMethod("emptyQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(getRequestWithOffset(reference, ""), parameter, reference);
	}

	@Test // GH-2856
	void mergedQualifierIsUsedInParameterLookup() {

		var parameter = getParameterOfMethod("mergedQualifier");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(getRequestWithOffset(reference, "merged"), parameter, reference);
	}

	@Test
	void discoversOffsetWithPrefix() {

		sut.setPrefix("p_");

		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedToWithSut(getRequestWithParameterName("p_offset", reference), PARAMETER, reference);
	}

	@Test
	void discoversOffsetWithPrefixAndQualifier() {

		sut.setPrefix("p_");

		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedToWithSut(getRequestWithParameterName("p_hello_offset", reference), parameter, reference);
	}

	@Test
	void discoversOffsetWithPrefixAndCustomOffsetParameter() {

		sut.setPrefix("p_");
		sut.setOffsetParameter("cursor");

		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedToWithSut(getRequestWithParameterName("p_cursor", reference), PARAMETER, reference);
	}

	@Test
	void discoversOffsetWithPrefixQualifierAndCustomOffsetParameter() {

		sut.setPrefix("p_");
		sut.setOffsetParameter("cursor");

		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedToWithSut(getRequestWithParameterName("p_hello_cursor", reference), parameter, reference);
	}

	@Test
	void discoversOffsetWithPrefixQualifierAndCustomDelimiter() {

		sut.setPrefix("p_");
		sut.setOffsetParameter("cursor");
		sut.setQualifierDelimiter(".");

		var parameter = getParameterOfMethod("qualifiedOffset");
		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedToWithSut(getRequestWithParameterName("p_hello.cursor", reference), parameter, reference);
	}

	@Test
	void returnsNullWithPrefixIfNotSpecified() {

		sut.setPrefix("p_");

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffsetWithSut(request, PARAMETER);

		assertThat(position).isNull();
	}

	@Test
	void returnsOptionalParameterWithPrefix() {

		sut.setPrefix("p_");

		var request = MockServerHttpRequest.get("/foo?p_offset=5").build();
		var position = resolveOffsetWithSut(request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.of(ScrollPosition.offset(5)));
	}

	@Test
	void returnsEmptyOptionalWithPrefixIfNotSpecified() {

		sut.setPrefix("p_");

		var request = MockServerHttpRequest.get("/foo").build();
		var position = resolveOffsetWithSut(request, OPTIONAL_PARAMETER);

		assertThat(position).isEqualTo(Optional.empty());
	}

	@Test
	void returnsNullForInvalidValueWithPrefix() {

		sut.setPrefix("p_");

		var request = MockServerHttpRequest.get("/foo?p_offset=invalid_number").build();

		assertThat(resolveOffsetWithSut(request, PARAMETER)).isNull();
	}

	@Test
	void returnsNullForEmptyValueWithPrefix() {

		sut.setPrefix("p_");

		var request = MockServerHttpRequest.get("/foo?p_offset=").build();

		assertThat(resolveOffsetWithSut(request, PARAMETER)).isNull();
	}

	@Test
	void setPrefixNullResetsToDefault() {

		sut.setPrefix("p_");
		sut.setPrefix(null);

		var reference = ScrollPosition.offset(5);

		assertSupportedAndResolvedTo(getRequestWithOffset(reference, null), PARAMETER, reference);
	}

	@Nullable
	private static Object resolveOffset(MockServerHttpRequest request, MethodParameter parameter) {

		var resolver = new ReactiveOffsetScrollPositionHandlerMethodArgumentResolver();
		return resolver.resolveArgumentValue(parameter, null, MockServerWebExchange.from(request));
	}

	private void assertSupportedAndResolvedTo(MockServerHttpRequest request, MethodParameter parameter,
			OffsetScrollPosition position) {

		assertThat(sut.supportsParameter(parameter)).isTrue();

		var resolved = resolveOffset(request, parameter);
		assertThat(resolved).isEqualTo(position);
	}

	private static MethodParameter getParameterOfMethod(String name) {
		return TestUtils.getParameterOfMethod(Controller.class, name, OffsetScrollPosition.class);
	}

	private static MethodParameter getOptionalParameterOfMethod(String name) {
		return TestUtils.getParameterOfMethod(Controller.class, name, Optional.class);
	}

	private static MockServerHttpRequest getRequestWithOffset(@Nullable OffsetScrollPosition position,
			@Nullable String qualifier) {

		if (position == null) {
			return TestUtils.getWebfluxRequest();
		}

		String parameterName = StringUtils.hasLength(qualifier) ? qualifier + "_offset" : "offset";
		return MockServerHttpRequest.get(String.format("foo?%s=%s", parameterName, String.valueOf(position.getOffset())))
				.build();
	}

	private static MockServerHttpRequest getRequestWithParameterName(String parameterName, OffsetScrollPosition position) {

		return MockServerHttpRequest.get(String.format("foo?%s=%s", parameterName, String.valueOf(position.getOffset())))
				.build();
	}

	@Nullable
	private Object resolveOffsetWithSut(MockServerHttpRequest request, MethodParameter parameter) {
		return sut.resolveArgumentValue(parameter, null, MockServerWebExchange.from(request));
	}

	private void assertSupportedAndResolvedToWithSut(MockServerHttpRequest request, MethodParameter parameter,
			OffsetScrollPosition position) {

		assertThat(sut.supportsParameter(parameter)).isTrue();

		var resolved = resolveOffsetWithSut(request, parameter);
		assertThat(resolved).isEqualTo(position);
	}

	interface Controller {

		void supportedMethod(OffsetScrollPosition offset);

		void supportedMethodWithOptional(Optional<OffsetScrollPosition> offset);

		void unsupportedMethod(String string);

		void qualifiedOffset(@Qualifier("hello") OffsetScrollPosition offset);

		void emptyQualifier(@Qualifier OffsetScrollPosition offset);

		void mergedQualifier(@TestQualifier OffsetScrollPosition offset);
	}
}
